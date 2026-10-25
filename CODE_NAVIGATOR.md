# AgriShield AI - Project Code Navigator & Cheat Sheet

This document is your **instant reference cheat sheet**. Whenever an interviewer, examiner, or colleague asks you to show where a specific component is located, use the tables and explanations below.

---

## 🧭 Quick Spotting Index

| If Someone Asks: | Open This File: | Key Method / Section: |
| :--- | :--- | :--- |
| **"Where is the Dataset Loading & Augmentation Code?"** | [`ml_training/01_dataset_loader_and_augmentation.py`](ml_training/01_dataset_loader_and_augmentation.py) | `load_plant_datasets()`, `get_data_augmentation_pipeline()` |
| **"Where is the Main ML Model / Transfer Learning Code?"** | [`ml_training/02_train_mobilenetv2_model.py`](ml_training/02_train_mobilenetv2_model.py) | `build_mobilenetv2_architecture()`, `fit()` |
| **"Where is the Model Evaluation & Accuracy Metrics Code?"** | [`ml_training/03_evaluate_model_metrics.py`](ml_training/03_evaluate_model_metrics.py) | `evaluate()`, `plot_and_save_confusion_matrix()` |
| **"Where is the Model Quantization & TFLite Conversion Code?"** | [`ml_training/04_convert_and_quantize_tflite.py`](ml_training/04_convert_and_quantize_tflite.py) | `convert_and_quantize()`, `verify_tflite_inference()` |
| **"Where is the On-Device AI Inference Code (in Android App)?"** | [`agrishield_app/android/app/src/main/java/com/agrishield/ai/ml/TFliteClassifier.java`](agrishield_app/android/app/src/main/java/com/agrishield/ai/ml/TFliteClassifier.java) | `classifyImageSync()`, `Interpreter.run()` |
| **"Where is the Disease Dataset & Treatment Database?"** | [`agrishield_app/android/app/src/main/assets/disease_info.json`](agrishield_app/android/app/src/main/assets/disease_info.json) | Complete 38-disease pathology knowledge base |
| **"Where is the SQLite Database Engine for Treatments?"** | [`agrishield_app/android/app/src/main/java/com/agrishield/ai/database/DatabaseHelper.java`](agrishield_app/android/app/src/main/java/com/agrishield/ai/database/DatabaseHelper.java) | `onCreate()`, `seedInitialData()`, `getDiseaseById()` |
| **"Where is the Camera & Image Capture Code?"** | [`agrishield_app/android/app/src/main/java/com/agrishield/ai/ui/main/MainActivity.java`](agrishield_app/android/app/src/main/java/com/agrishield/ai/ui/main/MainActivity.java) | `dispatchTakePictureIntent()`, `cameraLauncher` |
| **"Where is Image Auto-Rotation & OOM Safe Downsampling?"** | [`agrishield_app/android/app/src/main/java/com/agrishield/ai/ui/detection/DetectionActivity.java`](agrishield_app/android/app/src/main/java/com/agrishield/ai/ui/detection/DetectionActivity.java) | `rotateBitmapIfNeeded()`, `decodeSampledBitmapFromFile()` |
| **"Where is the Multilingual Localization (English, Hindi, Gujarati)?"** | [`agrishield_app/android/app/src/main/res/values/strings.xml`](agrishield_app/android/app/src/main/res/values/strings.xml)<br>[`values-hi/strings.xml`](agrishield_app/android/app/src/main/res/values-hi/strings.xml)<br>[`values-gu/strings.xml`](agrishield_app/android/app/src/main/res/values-gu/strings.xml) | In-app language switcher powered by `LocaleHelper.java` |

---

## 🔬 Detailed Breakdown: What Each File Does

### 1. Machine Learning Pipeline (`ml_training/`)

#### [`01_dataset_loader_and_augmentation.py`](ml_training/01_dataset_loader_and_augmentation.py)
- **Role**: Dataset loading, parsing, and data augmentation.
- **What to say**: *"This script loads the 38-class PlantVillage leaf dataset, creates a deterministic 80/20 train/validation split, and applies data augmentation (RandomFlip, Rotation, Zoom, Translation) to avoid overfitting before feeding into the network."*
- **CLI inspection**: `python 01_dataset_loader_and_augmentation.py --dataset_dir ./dataset/PlantVillage`

#### [`02_train_mobilenetv2_model.py`](ml_training/02_train_mobilenetv2_model.py)
- **Role**: Main neural network architecture and training loop.
- **What to say**: *"Here is our core computer vision model. We use Transfer Learning with MobileNetV2 pretrained on ImageNet. Inputs are normalized to `[-1.0, 1.0]`. In Phase 1, we freeze the base model and train the dense classification head with Dropout and Batch Normalization. In Phase 2, we unfreeze the top 40 layers and fine-tune with a low learning rate (1e-5)."*

#### [`03_evaluate_model_metrics.py`](ml_training/03_evaluate_model_metrics.py)
- **Role**: Model evaluation and metrics generation.
- **What to say**: *"This script calculates Top-1 and Top-3 accuracy on the test set, generates per-class Precision, Recall, and F1-Scores, and plots the Confusion Matrix heatmap to verify there are no misclassifications between visually similar diseases."*

#### [`04_convert_and_quantize_tflite.py`](ml_training/04_convert_and_quantize_tflite.py)
- **Role**: Model compression, quantization, and mobile export.
- **What to say**: *"This script converts the trained Keras model into a TensorFlow Lite flatbuffer. We apply Float16 quantization, reducing the model size from ~14 MB to ~3.9 MB with near-zero loss in accuracy, and automatically deploy the `.tflite` model and `labels.txt` directly to the Android app assets folder."*

---

### 2. Android App Source Code (`agrishield_app/android/`)

#### [`TFliteClassifier.java`](agrishield_app/android/app/src/main/java/com/agrishield/ai/ml/TFliteClassifier.java)
- **Role**: On-device AI inference engine.
- **What to say**: *"This is our on-device inference class. When a farmer captures a leaf, it loads the TFLite model from assets, resizes the image to 224x224 RGB, normalizes pixel intensities using the exact formula `(pixel / 127.5) - 1.0`, executes inference asynchronously without blocking the UI thread, and returns the Top-3 disease predictions with probability scores."*

#### [`DatabaseHelper.java`](agrishield_app/android/app/src/main/java/com/agrishield/ai/database/DatabaseHelper.java)
- **Role**: Offline SQLite disease advisory repository.
- **What to say**: *"AgriShield is an advisory system, not just a label predictor. This class initializes an embedded SQLite database on first launch and seeds all 38 disease profiles from `disease_info.json`. When a disease is detected, it retrieves symptoms, causes, recommended chemical pesticides with dosage, organic remedies, and preventive measures."*

#### [`disease_info.json`](agrishield_app/android/app/src/main/assets/disease_info.json)
- **Role**: The agricultural pathology dataset.
- **What to say**: *"This JSON file contains our structured agricultural knowledge base covering all 38 PlantVillage crop diseases with curated chemical dosages, organic biopesticides, active ingredients, and field precautions."*

#### [`MainActivity.java`](agrishield_app/android/app/src/main/java/com/agrishield/ai/ui/main/MainActivity.java)
- **Role**: Main dashboard and camera integration.
- **What to say**: *"Handles the modern AndroidX `ActivityResultContracts.TakePicture()` camera launcher, FileProvider URI sharing, runtime camera permission requests, and recent scan history display."*

#### [`DetectionActivity.java`](agrishield_app/android/app/src/main/java/com/agrishield/ai/ui/detection/DetectionActivity.java)
- **Role**: Image preprocessing, EXIF orientation correction, and AI classification runner.
- **What to say**: *"Safely downsamples camera images (`inSampleSize`) to eliminate `OutOfMemoryError` on high-resolution cameras, detects EXIF rotation to keep portrait photos upright, and triggers the AI classifier."*

---

## 📂 Repository Directory Tree

```
AgriShield AI/
├── CODE_NAVIGATOR.md                     <-- YOU ARE HERE (Quick Spotting Guide)
├── README.md                             <-- Project Overview & System Highlights
├── RESEARCH_AND_ARCHITECTURE.md          <-- Scientific Paper, Benchmarks & Deep Analysis
├── .gitignore                            <-- Git ignore rules
│
├── ml_training/                          <-- 🐍 MACHINE LEARNING PIPELINE
│   ├── 01_dataset_loader_and_augmentation.py   <-- Dataset Loading, Split & Augmentation
│   ├── 02_train_mobilenetv2_model.py           <-- Main Model Architecture & Training
│   ├── 03_evaluate_model_metrics.py            <-- Accuracy, Precision, Recall, Confusion Matrix
│   ├── 04_convert_and_quantize_tflite.py       <-- FP16 Quantization & Mobile Export
│   ├── requirements.txt                        <-- Python dependencies
│   └── README.md                               <-- ML Step-by-Step Guide
│
└── agrishield_app/android/               <-- 📱 NATIVE ANDROID APP
    ├── app/src/main/
    │   ├── AndroidManifest.xml           <-- Camera queries, permissions & providers
    │   ├── assets/
    │   │   ├── disease_info.json         <-- 🌾 38-Disease Pathology Knowledge Base
    │   │   ├── labels.txt                <-- Model class labels (38 classes)
    │   │   └── models/
    │   │       └── plant_disease_model.tflite <-- Compiled mobile neural network
    │   ├── java/com/agrishield/ai/
    │   │   ├── ml/
    │   │   │   └── TFliteClassifier.java <-- 🧠 On-device TFLite Inference Engine
    │   │   ├── database/
    │   │   │   └── DatabaseHelper.java   <-- 💾 SQLite Disease Database Helper
    │   │   ├── ui/                       <-- UI Activities (Main, Detection, Result, History)
    │   │   └── utils/
    │   │       └── LocaleHelper.java     <-- In-app Language Switcher
    │   └── res/                          <-- Layouts, Drawables, Multi-language Strings
    └── build.gradle.kts                  <-- Android Gradle build script (compileSdk=34)
```
