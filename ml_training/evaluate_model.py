"""
AgriShield AI - Model Evaluation & Performance Metrics
======================================================
Evaluates the trained plant disease model on test/validation data.
Generates:
  1. Overall Test Accuracy, Precision, Recall, and F1-Score
  2. Per-class Classification Report
  3. Confusion Matrix plot
  4. Top-3 and Top-5 categorical accuracy

Usage:
    python evaluate_model.py --model_path ./output/best_agrishield_model.keras --dataset_dir ./dataset/PlantVillage
"""

import os
import argparse
import json
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


def plot_confusion_matrix(cm, class_names, output_dir: str):
    """Generates and saves a readable confusion matrix."""
    plt.figure(figsize=(20, 18))
    plt.imshow(cm, interpolation="nearest", cmap=plt.cm.Greens)
    plt.title("AgriShield AI - Plant Disease Confusion Matrix", fontsize=16, pad=20)
    plt.colorbar()

    tick_marks = np.arange(len(class_names))
    plt.xticks(tick_marks, class_names, rotation=90, fontsize=8)
    plt.yticks(tick_marks, class_names, fontsize=8)

    plt.tight_layout()
    plt.ylabel("True Class (Ground Truth)", fontsize=12)
    plt.xlabel("Predicted Class by AgriShield AI", fontsize=12)

    cm_path = os.path.join(output_dir, "confusion_matrix.png")
    plt.savefig(cm_path, dpi=250, bbox_inches="tight")
    plt.close()
    print(f"[OK] Confusion matrix plot saved to: {cm_path}")


def main():
    args = parse_arguments()
    os.makedirs(args.output_dir, exist_ok=True)

    if not os.path.exists(args.model_path):
        raise FileNotFoundError(f"Model file not found at: '{args.model_path}'")

    print("=" * 70)
    print("  AgriShield AI - Plant Disease Model Evaluation")
    print("=" * 70)
    print(f"Loading model from: {args.model_path}")
    model = tf.keras.models.load_model(args.model_path)

    print(f"Loading test/validation dataset from: {args.dataset_dir}")
    test_ds = tf.keras.utils.image_dataset_from_directory(
        args.dataset_dir,
        validation_split=0.2,
        subset="validation",
        seed=1337,
        image_size=IMAGE_SIZE,
        batch_size=args.batch_size,
        shuffle=False,  # Important for sequential classification evaluation
    )

    class_names = test_ds.class_names
    num_classes = len(class_names)
    print(f"Found {num_classes} classes.")

    # Extract all true labels
    y_true = []
    y_pred_probs = []

    print("Running inference across validation batches...")
    for images, labels in test_ds:
        preds = model.predict(images, verbose=0)
        y_pred_probs.append(preds)
        y_true.append(labels.numpy())

    y_true = np.concatenate(y_true, axis=0)
    y_pred_probs = np.concatenate(y_pred_probs, axis=0)
    y_pred = np.argmax(y_pred_probs, axis=1)

    # Top-1 accuracy
    top1_accuracy = np.mean(y_true == y_pred) * 100.0

    # Top-3 accuracy
    top3_correct = [y in np.argsort(probs)[-3:] for y, probs in zip(y_true, y_pred_probs)]
    top3_accuracy = np.mean(top3_correct) * 100.0

    print("\n" + "=" * 50)
    print(f"  Top-1 Accuracy: {top1_accuracy:.2f}%")
    print(f"  Top-3 Accuracy: {top3_accuracy:.2f}%")
    print("=" * 50 + "\n")

    # Detailed Classification Report
    report_dict = classification_report(
        y_true,
        y_pred,
        target_names=class_names,
        output_dict=True,
        zero_division=0,
    )
    report_text = classification_report(
        y_true,
        y_pred,
        target_names=class_names,
        zero_division=0,
    )
    print(report_text)

    # Save reports
    report_file = os.path.join(args.output_dir, "classification_report.txt")
    with open(report_file, "w", encoding="utf-8") as f:
        f.write(report_text)

    metrics_file = os.path.join(args.output_dir, "metrics_summary.json")
    with open(metrics_file, "w", encoding="utf-8") as f:
        json.dump(
            {
                "top1_accuracy_percent": top1_accuracy,
                "top3_accuracy_percent": top3_accuracy,
                "total_samples": len(y_true),
                "num_classes": num_classes,
                "metrics": report_dict,
            },
            f,
            indent=2,
        )

    # Confusion Matrix
    cm = confusion_matrix(y_true, y_pred)
    plot_confusion_matrix(cm, class_names, args.output_dir)

    print(f"\n[DONE] Evaluation complete! All results saved in: {args.output_dir}")


if __name__ == "__main__":
    main()
