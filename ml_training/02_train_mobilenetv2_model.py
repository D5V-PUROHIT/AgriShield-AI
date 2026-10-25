"""
AgriShield AI - Step 2: Main Deep Learning Model Training (MobileNetV2 Transfer Learning)
=========================================================================================
Role:
    The central Machine Learning training code:
    1. Builds MobileNetV2 architecture pretrained on ImageNet
    2. Attaches custom classification head (Dropout, Dense(256), BatchNorm, Softmax)
    3. Normalizes images via MobileNetV2 formula: (Pixel / 127.5) - 1.0 -> [-1.0, 1.0]
    4. Two-phase training:
       - Phase 1: Feature extraction with frozen base model
       - Phase 2: Fine-tuning top 40 MobileNetV2 layers with reduced learning rate
    5. Saves best weights (.keras), training loss/accuracy curves, and class labels

Usage:
    python 02_train_mobilenetv2_model.py --dataset_dir ./dataset/PlantVillage --epochs 20 --batch_size 32
"""

import os
import sys
import argparse
import matplotlib.pyplot as plt
import tensorflow as tf
from tensorflow.keras import layers, models, callbacks
from tensorflow.keras.applications import MobileNetV2
from tensorflow.keras.applications.mobilenet_v2 import preprocess_input

# Import dataset loader & augmentation from Step 1
from importlib import import_module
step1_module = import_module("01_dataset_loader_and_augmentation")
load_plant_datasets = step1_module.load_plant_datasets
get_data_augmentation_pipeline = step1_module.get_data_augmentation_pipeline
INPUT_SHAPE = step1_module.INPUT_SHAPE


def parse_arguments():
    parser = argparse.ArgumentParser(description="Train AgriShield AI MobileNetV2 Plant Disease Model")
    parser.add_argument("--dataset_dir", type=str, default="./dataset/PlantVillage", help="Path to PlantVillage directory")
    parser.add_argument("--output_dir", type=str, default="./output", help="Directory where trained models and plots are saved")
    parser.add_argument("--epochs", type=int, default=20, help="Phase 1 training epochs")
    parser.add_argument("--batch_size", type=int, default=32, help="Mini-batch size")
    parser.add_argument("--learning_rate", type=float, default=1e-3, help="Phase 1 initial learning rate")
    parser.add_argument("--val_split", type=float, default=0.2, help="Validation fraction (0.2 = 20%)")
    parser.add_argument("--fine_tune_epochs", type=int, default=10, help="Phase 2 fine-tuning epochs")
    return parser.parse_args()


def build_mobilenetv2_architecture(num_classes: int):
    """
    Constructs the MobileNetV2 Transfer Learning Architecture.
    
    Layers:
    1. Input layer: (224, 224, 3)
    2. Data Augmentation: Random Flip, Rotation, Zoom, Translation (training only)
    3. Normalization: MobileNetV2 preprocess_input maps [0, 255] -> [-1.0, 1.0]
    4. Base Feature Extractor: MobileNetV2 (weights='imagenet', pooling='avg')
    5. Classification Head:
       - Dropout(0.3)
       - Dense(256, activation='relu')
       - BatchNormalization()
       - Dropout(0.2)
       - Dense(num_classes, activation='softmax')
    """
    print("[*] Assembling MobileNetV2 transfer learning neural network...")
    data_augmentation = get_data_augmentation_pipeline()

    inputs = layers.Input(shape=INPUT_SHAPE, name="leaf_image_input")

    # Step 1: Augmentation
    x = data_augmentation(inputs)

    # Step 2: Normalization [-1.0, 1.0]
    x = layers.Lambda(preprocess_input, name="mobilenetv2_normalization")(x)

    # Step 3: MobileNetV2 Base (frozen initially)
    base_model = MobileNetV2(
        input_shape=INPUT_SHAPE,
        include_top=False,
        weights="imagenet",
        pooling="avg",
    )
    base_model.trainable = False
    x = base_model(x, training=False)

    # Step 4: Classification Head
    x = layers.Dropout(0.3, name="head_dropout_1")(x)
    x = layers.Dense(256, activation="relu", name="dense_features")(x)
    x = layers.BatchNormalization(name="batch_norm")(x)
    x = layers.Dropout(0.2, name="head_dropout_2")(x)
    outputs = layers.Dense(num_classes, activation="softmax", dtype="float32", name="disease_prediction")(x)

    model = models.Model(inputs=inputs, outputs=outputs, name="AgriShield_MobileNetV2")
    return model, base_model


def export_class_labels(class_names, output_dir: str):
    """Exports class labels in the exact numerical index order expected by Android app."""
    os.makedirs(output_dir, exist_ok=True)
    labels_file = os.path.join(output_dir, "labels.txt")
    with open(labels_file, "w", encoding="utf-8") as f:
        for name in class_names:
            f.write(f"{name}\n")
    print(f"[*] Saved class labels to: {labels_file}")
    return labels_file


def save_training_plots(history, output_dir: str):
    """Plots and saves accuracy and loss curves for performance monitoring."""
    acc = history.history.get("accuracy", [])
    val_acc = history.history.get("val_accuracy", [])
    loss = history.history.get("loss", [])
    val_loss = history.history.get("val_loss", [])

    epochs_range = range(len(acc))

    plt.figure(figsize=(12, 5))
    plt.subplot(1, 2, 1)
    plt.plot(epochs_range, acc, label="Training Accuracy", color="#2e7d32", linewidth=2)
    plt.plot(epochs_range, val_acc, label="Validation Accuracy", color="#ff9800", linewidth=2)
    plt.legend(loc="lower right")
    plt.title("Model Accuracy Across Epochs")
    plt.grid(True, linestyle="--", alpha=0.5)

    plt.subplot(1, 2, 2)
    plt.plot(epochs_range, loss, label="Training Loss", color="#d32f2f", linewidth=2)
    plt.plot(epochs_range, val_loss, label="Validation Loss", color="#1976d2", linewidth=2)
    plt.legend(loc="upper right")
    plt.title("Model Loss Across Epochs")
    plt.grid(True, linestyle="--", alpha=0.5)

    plot_path = os.path.join(output_dir, "training_curves.png")
    plt.tight_layout()
    plt.savefig(plot_path, dpi=200)
    plt.close()
    print(f"[*] Training curves plot saved to: {plot_path}")


def main():
    args = parse_arguments()
    os.makedirs(args.output_dir, exist_ok=True)

    print("=" * 75)
    print("  AgriShield AI - MobileNetV2 Plant Disease Model Training")
    print("=" * 75)

    # 1. Load Data
    train_ds, val_ds, class_names = load_plant_datasets(args.dataset_dir, args.batch_size, args.val_split)
    export_class_labels(class_names, args.output_dir)

    # 2. Build Model
    model, base_model = build_mobilenetv2_architecture(len(class_names))
    model.summary()

    # 3. Phase 1 - Train Top Classification Head
    print(f"\n[Phase 1] Training classification head ({args.epochs} epochs)...")
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=args.learning_rate),
        loss="categorical_crossentropy",
        metrics=["accuracy"],
    )

    best_model_path = os.path.join(args.output_dir, "best_agrishield_model.keras")
    train_callbacks = [
        callbacks.ModelCheckpoint(
            best_model_path,
            monitor="val_accuracy",
            mode="max",
            save_best_only=True,
            verbose=1,
        ),
        callbacks.EarlyStopping(
            monitor="val_accuracy",
            patience=5,
            restore_best_weights=True,
            verbose=1,
        ),
        callbacks.ReduceLROnPlateau(
            monitor="val_loss",
            factor=0.3,
            patience=2,
            min_lr=1e-6,
            verbose=1,
        ),
    ]

    history_phase1 = model.fit(
        train_ds,
        validation_data=val_ds,
        epochs=args.epochs,
        callbacks=train_callbacks,
    )

    # 4. Phase 2 - Fine-Tuning Top 40 Layers of MobileNetV2
    if args.fine_tune_epochs > 0:
        print(f"\n[Phase 2] Fine-tuning top 40 MobileNetV2 layers ({args.fine_tune_epochs} epochs)...")
        base_model.trainable = True
        fine_tune_at = len(base_model.layers) - 40
        for layer in base_model.layers[:fine_tune_at]:
            layer.trainable = False

        model.compile(
            optimizer=tf.keras.optimizers.Adam(learning_rate=1e-5),
            loss="categorical_crossentropy",
            metrics=["accuracy"],
        )

        history_phase2 = model.fit(
            train_ds,
            validation_data=val_ds,
            epochs=args.epochs + args.fine_tune_epochs,
            initial_epoch=history_phase1.epoch[-1] + 1 if history_phase1.epoch else 0,
            callbacks=train_callbacks,
        )
        save_training_plots(history_phase2, args.output_dir)
    else:
        save_training_plots(history_phase1, args.output_dir)

    # 5. Export Final Model
    final_model_path = os.path.join(args.output_dir, "plant_disease_model.keras")
    model.save(final_model_path)
    print(f"\n[OK] Model Training Complete! Saved to: {final_model_path}")
    print(f"      Next Step: Run '03_evaluate_model_metrics.py' or '04_convert_and_quantize_tflite.py'")


if __name__ == "__main__":
    main()
