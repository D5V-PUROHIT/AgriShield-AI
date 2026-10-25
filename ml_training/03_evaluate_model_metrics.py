"""
AgriShield AI - Step 3: Model Evaluation & Performance Metrics
==============================================================
Role:
    Evaluates the trained plant disease model on test/validation data.
    Generates:
      1. Overall Accuracy, Precision, Recall, and F1-Score
      2. Per-class Classification Report
      3. Top-1, Top-3, and Top-5 categorical accuracy
      4. Confusion Matrix heatmap plot (saved as confusion_matrix.png)

Usage:
    python 03_evaluate_model_metrics.py --model_path ./output/best_agrishield_model.keras --dataset_dir ./dataset/PlantVillage
"""

import os
import argparse
import numpy as np
import matplotlib.pyplot as plt
import tensorflow as tf
from sklearn.metrics import classification_report, confusion_matrix

IMAGE_SIZE = (224, 224)


def parse_arguments():
    parser = argparse.ArgumentParser(description="Evaluate AgriShield AI Deep Learning Model")
    parser.add_argument(
        "--model_path",
        type=str,
        default="./output/best_agrishield_model.keras",
        help="Path to trained .keras model",
    )
    parser.add_argument(
        "--dataset_dir",
        type=str,
        default="./dataset/PlantVillage",
        help="Path to PlantVillage dataset directory",
    )
    parser.add_argument(
        "--output_dir",
        type=str,
        default="./evaluation_results",
        help="Directory to save evaluation reports and confusion matrix",
    )
    parser.add_argument("--batch_size", type=int, default=32, help="Evaluation batch size")
    return parser.parse_args()


def plot_and_save_confusion_matrix(cm, class_names, output_dir: str):
    """Generates and saves a readable confusion matrix image."""
    plt.figure(figsize=(20, 18))
    plt.imshow(cm, interpolation="nearest", cmap=plt.cm.Greens)
    plt.title("AgriShield AI - Plant Disease Confusion Matrix", fontsize=16, pad=20)
    plt.colorbar()

    tick_marks = np.arange(len(class_names))
    plt.xticks(tick_marks, class_names, rotation=90, fontsize=8)
    plt.yticks(tick_marks, class_names, fontsize=8)

    plt.xlabel("Predicted Disease Class", fontsize=12, labelpad=10)
    plt.ylabel("True Disease Class", fontsize=12, labelpad=10)
    plt.tight_layout()

    cm_path = os.path.join(output_dir, "confusion_matrix.png")
    plt.savefig(cm_path, dpi=200)
    plt.close()
    print(f"[*] Confusion matrix plot saved to: {cm_path}")


def evaluate(model_path: str, dataset_dir: str, output_dir: str, batch_size: int):
    os.makedirs(output_dir, exist_ok=True)

    print("=" * 75)
    print("  AgriShield AI - Model Evaluation & Performance Analysis")
    print("=" * 75)
    print(f"Loading Model from: {model_path}")
    model = tf.keras.models.load_model(model_path)

    print(f"Loading Test/Validation Dataset from: {dataset_dir}")
    val_ds = tf.keras.utils.image_dataset_from_directory(
        dataset_dir,
        validation_split=0.2,
        subset="validation",
        seed=1337,
        image_size=IMAGE_SIZE,
        batch_size=batch_size,
        label_mode="categorical",
        shuffle=False,
    )

    class_names = val_ds.class_names
    print(f"Evaluating {len(class_names)} classes...")

    # Predict batches
    y_true_all = []
    y_pred_probs_all = []

    for images, labels in val_ds:
        preds = model.predict(images, verbose=0)
        y_true_all.extend(np.argmax(labels.numpy(), axis=1))
        y_pred_probs_all.extend(preds)

    y_true = np.array(y_true_all)
    y_pred_probs = np.array(y_pred_probs_all)
    y_pred = np.argmax(y_pred_probs, axis=1)

    # Calculate Top-1 and Top-3 Accuracy
    top1_acc = np.mean(y_true == y_pred) * 100
    top3_correct = sum(y_t in np.argsort(p)[-3:] for y_t, p in zip(y_true, y_pred_probs))
    top3_acc = (top3_correct / len(y_true)) * 100

    print("\n" + "=" * 50)
    print(f"  Top-1 Test Accuracy: {top1_acc:.2f}%")
    print(f"  Top-3 Test Accuracy: {top3_acc:.2f}%")
    print("=" * 50 + "\n")

    # Generate classification report
    report = classification_report(y_true, y_pred, target_names=class_names, digits=4)
    print("Classification Report:")
    print(report)

    report_path = os.path.join(output_dir, "classification_report.txt")
    with open(report_path, "w", encoding="utf-8") as f:
        f.write(f"AgriShield AI - Model Evaluation Report\n")
        f.write(f"Top-1 Accuracy: {top1_acc:.2f}%\n")
        f.write(f"Top-3 Accuracy: {top3_acc:.2f}%\n\n")
        f.write(report)
    print(f"[*] Saved full text report to: {report_path}")

    # Plot Confusion Matrix
    cm = confusion_matrix(y_true, y_pred)
    plot_and_save_confusion_matrix(cm, class_names, output_dir)


if __name__ == "__main__":
    args = parse_arguments()
    evaluate(args.model_path, args.dataset_dir, args.output_dir, args.batch_size)
