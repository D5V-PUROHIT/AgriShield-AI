# AgriShield AI - Deep Learning Model Training Pipeline

This folder contains the complete deep learning training, evaluation, and mobile model export scripts for **AgriShield AI**.

---

## 🌾 Model Architecture Overview
- **Base Architecture**: MobileNetV2 (Pre-trained on ImageNet)
- **Input Resolution**: `224 × 224 × 3` (RGB)
- **Normalization Formula**:
  $$\text{Normalized} = \frac{\text{Pixel}}{127.5} - 1.0$$
  This maps raw `[0, 255]` pixel intensities strictly to the range `[-1.0, 1.0]`. Both the Python training pipeline and the Flutter `TfliteClassifier` use this exact formula.
- **Dataset**: PlantVillage (38 crop and disease classes)
- **Target Target Output**: 38 Softmax class probability distribution

---

## 📁 Directory Structure
```
/ml_training
  ├── train_model.py          # MobileNetV2 transfer learning + fine-tuning
  ├── evaluate_model.py       # Accuracy, classification report, confusion matrix
  ├── convert_to_tflite.py    # Keras -> TFLite converter with quantization
  ├── requirements.txt        # Python dependencies
  └── README.md               # Training and Colab instructions
```

---

## ⚡ Quick Start: Train on Google Colab (Recommended)

Google Colab provides free GPU (T4) compute to train the model in ~15 minutes.

### Step 1: Open Google Colab
1. Go to [colab.research.google.com](https://colab.research.google.com).
2. Create a **New Notebook** and set runtime to **GPU**:  
   `Runtime > Change runtime type > T4 GPU`.

### Step 2: Download PlantVillage Dataset
In a Colab cell, download the PlantVillage dataset:
```python
# Download PlantVillage dataset (Color segmented / raw)
!git clone https://github.com/spMohanty/PlantVillage-Dataset.git
dataset_dir = "/content/PlantVillage-Dataset/raw/color"
```
*(Or use Kaggle API: `kaggle datasets download -d emmarex/plantdisease`)*

### Step 3: Install Requirements
```bash
!pip install tensorflow numpy opencv-python scikit-learn matplotlib
```

### Step 4: Run Training Script
Upload `train_model.py` to Colab and run:
```bash
python train_model.py \
  --dataset_dir /content/PlantVillage-Dataset/raw/color \
  --epochs 20 \
  --batch_size 32 \
  --learning_rate 0.001 \
  --fine_tune_epochs 10 \
  --output_dir /content/output
```

### Step 5: Convert to TFLite
Upload `convert_to_tflite.py` and run:
```bash
python convert_to_tflite.py \
  --model_path /content/output/best_agrishield_model.keras \
  --labels_path /content/output/labels.txt \
  --quantize float16 \
  --output_dir /content/output
```

### Step 6: Download Files to Flutter App
Download the exported files:
- `/content/output/plant_disease_model.tflite` ➔ save to `agrishield_app/assets/models/plant_disease_model.tflite`
- `/content/output/labels.txt` ➔ save to `agrishield_app/assets/labels.txt`

---

## 💻 Local Machine Training Instructions

### 1. Set Up Python Virtual Environment
```bash
# Using Python 3.10 or 3.11
python -m venv venv
# Windows:
venv\Scripts\activate
# Linux/Mac:
source venv/bin/activate

pip install -r requirements.txt
```

### 2. Run Training
```bash
python train_model.py --dataset_dir ./dataset/PlantVillage --epochs 20 --output_dir ./output
```

### 3. Evaluate Model
```bash
python evaluate_model.py --model_path ./output/best_agrishield_model.keras --dataset_dir ./dataset/PlantVillage
```
This generates `confusion_matrix.png`, `classification_report.txt`, and `metrics_summary.json`.

### 4. Convert to TFLite
```bash
python convert_to_tflite.py --model_path ./output/best_agrishield_model.keras --quantize float16
```
This will automatically place `plant_disease_model.tflite` and `labels.txt` in `../agrishield_app/assets/`.

---

## 🧪 Testing Prior to Training (Placeholder Generator)
If you wish to test the Flutter app's complete on-device TFLite pipeline immediately without running full training, simply execute:
```bash
python convert_to_tflite.py --generate_placeholder_model
```
This builds an authentic MobileNetV2 mobile TFLite flatbuffer and places it directly into the Flutter app's assets!
