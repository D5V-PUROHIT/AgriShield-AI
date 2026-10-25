"""
AgriShield AI - Plant Disease Detection Model Training
======================================================
Architecture: MobileNetV2 Transfer Learning
Input Shape: (224, 224, 3)
Normalization: MobileNetV2 preprocessing maps [0, 255] -> [-1.0, 1.0] via (x / 127.5) - 1.0
Target Classes: 38 PlantVillage leaf disease & healthy classes

Usage:
    python train_model.py --dataset_dir ./dataset/PlantVillage --epochs 25 --batch_size 32
"""

import os
import sys
import argparse
import json
import matplotlib.pyplot as plt
import tensorflow as tf
from tensorflow.keras import layers, models, callbacks
from tensorflow.keras.applications import MobileNetV2
from tensorflow.keras.applications.mobilenet_v2 import preprocess_input

# Constant input dimension (strictly matching Flutter TfliteClassifier)
IMAGE_SIZE = (224, 224)
INPUT_SHAPE = (224, 224, 3)


def parse_arguments():
    parser = argparse.ArgumentParser(description="Train AgriShield AI MobileNetV2 Plant Disease Model")
    parser.add_argument(
        "--dataset_dir",
        type=str,
        default="./dataset/PlantVillage",
        help="Path to PlantVillage directory containing class subfolders",
    )
    parser.add_argument(
        "--output_dir",
        type=str,
        default="./output",
        help="Directory where trained model, labels, and plots will be saved",
    )
    parser.add_argument("--epochs", type=int, default=20, help="Number of training epochs")
    parser.add_argument("--batch_size", type=int, default=32, help="Mini-batch size")
    parser.add_argument("--learning_rate", type=float, default=1e-3, help="Initial learning rate")
    parser.add_argument("--val_split", type=float, default=0.2, help="Validation data fraction (e.g. 0.2 = 20%)")
    parser.add_argument("--fine_tune_epochs", type=int, default=10, help="Epochs for fine-tuning top layers")
    return parser.parse_args()


def load_datasets(dataset_dir: str, batch_size: int, val_split: float):
    """
    Loads training and validation datasets from subdirectories using image_dataset_from_directory.
    Ensures deterministic split using a fixed seed.
    """
    if not os.path.exists(dataset_dir):
        raise FileNotFoundError(
            f"Dataset directory not found at: '{dataset_dir}'.\n"
            f"Please download the PlantVillage dataset from Kaggle or GitHub and provide the path."
        )

    print(f"[1/5] Loading datasets from: {dataset_dir}")
    train_ds = tf.keras.utils.image_dataset_from_directory(
        dataset_dir,
        validation_split=val_split,
        subset="training",
        seed=1337,
        image_size=IMAGE_SIZE,
        batch_size=batch_size,
        label_mode="categorical",
    )

    val_ds = tf.keras.utils.image_dataset_from_directory(
        dataset_dir,
        validation_split=val_split,
        subset="validation",
        seed=1337,
        image_size=IMAGE_SIZE,
        batch_size=batch_size,
        label_mode="categorical",
    )

    class_names = train_ds.class_names
    num_classes = len(class_names)
    print(f"       Found {num_classes} classes.")

    # Configure dataset for performance with prefetching and parallel reads
    autotune = tf.data.AUTOTUNE
    train_ds = train_ds.cache().shuffle(1000).prefetch(buffer_size=autotune)
    val_ds = val_ds.cache().prefetch(buffer_size=autotune)

    return train_ds, val_ds, class_names


def build_model(num_classes: int):
    """
    Constructs the MobileNetV2 transfer learning model.
    Data Preprocessing:
        - MobileNetV2 expects normalized inputs: x in [-1, 1], calculated as (x / 127.5) - 1.0.
        - tf.keras.applications.mobilenet_v2.preprocess_input is incorporated directly or applied to inputs.
    """
    print("[2/5] Building MobileNetV2 transfer learning architecture...")

    # Data augmentation pipeline (active only during training)
    data_augmentation = tf.keras.Sequential(
        [
            layers.RandomFlip("horizontal_and_vertical"),
            layers.RandomRotation(0.15),
            layers.RandomZoom(0.15),
            layers.RandomTranslation(0.1, 0.1),
        ],
        name="data_augmentation",
    )

    inputs = layers.Input(shape=INPUT_SHAPE, name="input_image")

    # Step 1: Augment (training mode only)
    x = data_augmentation(inputs)

    # Step 2: Normalize [0, 255] -> [-1, 1] using MobileNetV2 preprocess_input
    # Formula: (x / 127.5) - 1.0. This must match Flutter image preprocessing exactly!
    x = layers.Lambda(preprocess_input, name="mobilenetv2_normalization")(x)

    # Step 3: Base pre-trained model (weights='imagenet', frozen initially)
    base_model = MobileNetV2(
        input_shape=INPUT_SHAPE,
        include_top=False,
        weights="imagenet",
        pooling="avg",
    )
    base_model.trainable = False  # Freeze base model weights for phase 1
    x = base_model(x, training=False)

    # Step 4: Classification head with regularization
    x = layers.Dropout(0.3, name="top_dropout_1")(x)
    x = layers.Dense(256, activation="relu", name="dense_features")(x)
    x = layers.BatchNormalization(name="batch_norm")(x)
    x = layers.Dropout(0.2, name="top_dropout_2")(x)
    outputs = layers.Dense(num_classes, activation="softmax", dtype="float32", name="predictions")(x)

    model = models.Model(inputs=inputs, outputs=outputs, name="AgriShield_MobileNetV2")
    return model, base_model


def export_labels(class_names, output_dir: str):
    """
    Exports labels.txt in the exact order indexed by the model (0 to N-1).
    This file is copied directly to Flutter assets/labels.txt.
    """
    os.makedirs(output_dir, exist_ok=True)
    labels_file = os.path.join(output_dir, "labels.txt")
    with open(labels_file, "w", encoding="utf-8") as f:
        for name in class_names:
            f.write(f"{name}\n")
    print(f"       Saved labels to: {labels_file}")
    return labels_file


def plot_history(history, output_dir: str):
    """Plots and saves accuracy and loss curves."""
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
    plt.title("Training & Validation Accuracy")
    plt.grid(True, linestyle="--", alpha=0.5)

    plt.subplot(1, 2, 2)
    plt.plot(epochs_range, loss, label="Training Loss", color="#d32f2f", linewidth=2)
    plt.plot(epochs_range, val_loss, label="Validation Loss", color="#1976d2", linewidth=2)
    plt.legend(loc="upper right")
    plt.title("Training & Validation Loss")
    plt.grid(True, linestyle="--", alpha=0.5)

    plot_path = os.path.join(output_dir, "training_curves.png")
    plt.tight_layout()
    plt.savefig(plot_path, dpi=200)
    plt.close()
    print(f"       Saved training curve plot to: {plot_path}")


def main():
    args = parse_arguments()
    os.makedirs(args.output_dir, exist_ok=True)

    print("=" * 70)
    print("  AgriShield AI - Plant Disease MobileNetV2 Training Pipeline")
    print("=" * 70)
    print(f"TensorFlow Version: {tf.__version__}")
    print(f"GPU Available: {len(tf.config.list_physical_devices('GPU')) > 0}")

    # Step 1: Load Data
    train_ds, val_ds, class_names = load_datasets(args.dataset_dir, args.batch_size, args.val_split)
    export_labels(class_names, args.output_dir)

    # Step 2: Build Model
    model, base_model = build_model(len(class_names))
    model.summary()

    # Step 3: Phase 1 - Train Top Classification Head
    print(f"\n[3/5] Phase 1 Training: Feature extraction ({args.epochs} epochs)...")
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

    # Step 4: Phase 2 - Fine-Tuning top layers of MobileNetV2
    if args.fine_tune_epochs > 0:
        print(f"\n[4/5] Phase 2: Fine-Tuning top MobileNetV2 layers ({args.fine_tune_epochs} epochs)...")
        base_model.trainable = True
        # Unfreeze the last 40 layers of MobileNetV2 (leaving lower low-level edge/texture layers frozen)
        fine_tune_at = len(base_model.layers) - 40
        for layer in base_model.layers[:fine_tune_at]:
            layer.trainable = False

        model.compile(
            optimizer=tf.keras.optimizers.Adam(learning_rate=1e-5),  # very low lr for fine-tuning
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
        plot_history(history_phase2, args.output_dir)
    else:
        plot_history(history_phase1, args.output_dir)

    # Step 5: Save final model
    final_model_path = os.path.join(args.output_dir, "plant_disease_model.keras")
    model.save(final_model_path)
    print(f"\n[5/5] Training Complete! Saved final model to: {final_model_path}")
    print(f"       Next Step: Run 'python convert_to_tflite.py' to generate mobile .tflite weights.")


if __name__ == "__main__":
    main()
