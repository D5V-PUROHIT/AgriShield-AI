# AgriShield AI - Machine Learning Pipeline Guide

This folder contains the complete, modular deep learning training, evaluation, and mobile model export pipeline for **AgriShield AI**.

---

## 📁 Pipeline File Map (Numbered for Easy Identification)

| Step | Script Name | Exact Role / Function |
| :--- | :--- | :--- |
| **01** | [`01_dataset_loader_and_augmentation.py`](./01_dataset_loader_and_augmentation.py) | **Dataset Loading & Preprocessing**: Loads PlantVillage leaf images, performs class indexing, train/validation split, data augmentation (flip, rotation, zoom, translation), and dataset statistics inspection. |
| **02** | [`02_train_mobilenetv2_model.py`](./02_train_mobilenetv2_model.py) | **Main ML Model Architecture & Training**: Builds MobileNetV2 transfer learning model, classification head (Dropout, Dense(256), BatchNorm, Softmax), Phase 1 feature extraction, and Phase 2 fine-tuning. |
| **03** | [`03_evaluate_model_metrics.py`](./03_evaluate_model_metrics.py) | **Evaluation & Accuracy Metrics**: Evaluates model on test data, calculates Top-1/Top-3/Top-5 accuracy, Precision, Recall, F1-Score, and generates `confusion_matrix.png`. |
| **04** | [`04_convert_and_quantize_tflite.py`](./04_convert_and_quantize_tflite.py) | **Quantization & Mobile TFLite Export**: Converts `.keras` model into optimized `.tflite` mobile flatbuffer with Float16/dynamic quantization and syncs weights directly into Android assets. |
| **—** | [`requirements.txt`](./requirements.txt) | Python dependencies (`tensorflow`, `numpy`, `scikit-learn`, `matplotlib`). |

---

## 🌾 Model Architecture Overview
- **Base Architecture**: MobileNetV2 (Pre-trained on ImageNet)
- **Input Resolution**: `224 × 224 × 3` (RGB)
- **Normalization Formula**:
  $$\text{Normalized} = \frac{\text{Pixel}}{127.5} - 1.0$$
  Maps raw `[0, 255]` pixel intensities to `[-1.0, 1.0]`. Both the Python training pipeline and Android `TFliteClassifier.java` use this exact formula.
- **Dataset**: PlantVillage (38 crop and disease classes)
- **Target Output**: 38 Softmax class probability distribution

---

## ⚡ How to Run the Pipeline

### 1. Inspect Dataset
```bash
python 01_dataset_loader_and_augmentation.py --dataset_dir ./dataset/PlantVillage
```

### 2. Train MobileNetV2 Model
```bash
python 02_train_mobilenetv2_model.py --dataset_dir ./dataset/PlantVillage --epochs 20 --output_dir ./output
```

### 3. Evaluate Performance & Plot Confusion Matrix
```bash
python 03_evaluate_model_metrics.py --model_path ./output/best_agrishield_model.keras --dataset_dir ./dataset/PlantVillage
```

### 4. Convert to Optimized TFLite for Android
```bash
python 04_convert_and_quantize_tflite.py --model_path ./output/best_agrishield_model.keras --quantize float16
```
*(Automatically syncs `plant_disease_model.tflite` and `labels.txt` into `agrishield_app/android/app/src/main/assets/`)*
