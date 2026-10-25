"""
AgriShield AI - Step 1: Dataset Loader, Preprocessing & Augmentation Pipeline
==============================================================================
Role:
    Handles all dataset operations for PlantVillage plant leaf images:
    1. Loading images from subdirectories with class labels
    2. Deterministic Train/Validation/Test splitting
    3. Data Augmentation pipeline (RandomFlip, Rotation, Zoom, Translation)
    4. Dataset optimization (caching, prefetching, batching)
    5. Standalone CLI inspection of dataset statistics and class distribution

Usage (Standalone Dataset Inspection):
    python 01_dataset_loader_and_augmentation.py --dataset_dir ./dataset/PlantVillage
"""

import os
import argparse
import tensorflow as tf
from tensorflow.keras import layers
from tensorflow.keras.applications.mobilenet_v2 import preprocess_input

# Constant input dimensions matching mobile model and Android inference
IMAGE_SIZE = (224, 224)
INPUT_SHAPE = (224, 224, 3)


def get_data_augmentation_pipeline():
    """
    Constructs the data augmentation sequential layer pipeline.
    Active strictly during training mode to prevent overfitting on leaf photos.
    """
    return tf.keras.Sequential(
        [
            layers.RandomFlip("horizontal_and_vertical", name="aug_random_flip"),
            layers.RandomRotation(0.15, fill_mode="reflect", name="aug_random_rotation"),
            layers.RandomZoom(0.15, fill_mode="reflect", name="aug_random_zoom"),
            layers.RandomTranslation(0.1, 0.1, fill_mode="reflect", name="aug_random_translation"),
        ],
        name="data_augmentation",
    )


def load_plant_datasets(dataset_dir: str, batch_size: int = 32, val_split: float = 0.2, seed: int = 1337):
    """
    Loads training and validation datasets from PlantVillage subdirectories.
    
    Args:
        dataset_dir: Root directory containing class subfolders (e.g. 'Tomato___Early_blight')
        batch_size: Number of images per training batch
        val_split: Fraction of dataset allocated for validation (default: 20%)
        seed: Random seed for deterministic reproducibility

    Returns:
        train_ds: Cached and prefetched tf.data.Dataset for training
        val_ds: Cached and prefetched tf.data.Dataset for validation
        class_names: List of class strings in alphabetical order
    """
    if not os.path.exists(dataset_dir):
        raise FileNotFoundError(
            f"Dataset directory not found at: '{dataset_dir}'.\n"
            f"Please download the PlantVillage dataset and specify the folder path."
        )

    print(f"[*] Loading PlantVillage datasets from: {dataset_dir}")
    train_ds = tf.keras.utils.image_dataset_from_directory(
        dataset_dir,
        validation_split=val_split,
        subset="training",
        seed=seed,
        image_size=IMAGE_SIZE,
        batch_size=batch_size,
        label_mode="categorical",
    )

    val_ds = tf.keras.utils.image_dataset_from_directory(
        dataset_dir,
        validation_split=val_split,
        subset="validation",
        seed=seed,
        image_size=IMAGE_SIZE,
        batch_size=batch_size,
        label_mode="categorical",
    )

    class_names = train_ds.class_names
    num_classes = len(class_names)
    print(f"[*] Successfully indexed {num_classes} plant-disease classes.")

    # Configure dataset performance pipeline with in-memory caching and background prefetching
    autotune = tf.data.AUTOTUNE
    train_ds = train_ds.cache().shuffle(1000, seed=seed).prefetch(buffer_size=autotune)
    val_ds = val_ds.cache().prefetch(buffer_size=autotune)

    return train_ds, val_ds, class_names


def inspect_dataset_summary(dataset_dir: str):
    """Prints a detailed statistical breakdown of classes and image counts."""
    if not os.path.exists(dataset_dir):
        print(f"[ERROR] Directory does not exist: {dataset_dir}")
        return

    classes = [d for d in os.listdir(dataset_dir) if os.path.isdir(os.path.join(dataset_dir, d))]
    classes.sort()

    total_images = 0
    print("\n" + "=" * 75)
    print("  AgriShield AI - PlantVillage Dataset Distribution Summary")
    print("=" * 75)
    print(f"{'#':<4} {'Class Name':<55} {'Images':<10}")
    print("-" * 75)

    for idx, class_name in enumerate(classes, start=1):
        class_path = os.path.join(dataset_dir, class_name)
        images = [f for f in os.listdir(class_path) if f.lower().endswith(('.jpg', '.jpeg', '.png'))]
        count = len(images)
        total_images += count
        print(f"{idx:<4} {class_name:<55} {count:<10}")

    print("-" * 75)
    print(f"Total Classes: {len(classes)} | Total Images: {total_images}")
    print("=" * 75 + "\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="AgriShield AI Dataset Loader & Inspector")
    parser.add_argument("--dataset_dir", type=str, default="./dataset/PlantVillage", help="Path to PlantVillage dataset folder")
    parser.add_argument("--batch_size", type=int, default=32, help="Mini-batch size")
    args = parser.parse_args()

    inspect_dataset_summary(args.dataset_dir)
