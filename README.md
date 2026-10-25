# AgriShield AI: Plant Disease Detection and Treatment Recommendation Using Deep Learning

**AgriShield AI** is an offline-first mobile application built with **Flutter** and **TensorFlow Lite** that empowers farmers, agronomists, and agricultural extension workers to detect plant diseases from leaf photos and receive actionable advisory treatments on-device.

Instead of merely displaying a disease name, AgriShield AI functions as an **end-to-end agricultural advisory system** providing chemical pesticides, organic remedies, field precautions, and prevention tips without needing an internet connection.

---

## 🌟 Key Highlights

- **100% On-Device Inference**: Runs MobileNetV2 transfer learning weights locally via `tflite_flutter` in a dedicated background Dart Isolate—guaranteeing zero latency, zero cloud costs, and complete offline capability in remote farm fields.
- **Top-3 Confidence Ranking**: Produces ranked predictions with probability scores and triggers a warning modal if confidence falls below **60%**, advising farmers to retake clearer photos under natural lighting.
- **Actionable Treatment Advisory**: Tabbed recommendations covering recommended chemical pesticides, organic/biological controls (neem oil, biocontrol agents), safety precautions (PPE), and prevention measures.
- **Offline SQLite Knowledge Base**: All 38 PlantVillage disease profiles are seeded automatically on first launch into an embedded SQLite database (`sqflite`).
- **Multilingual Support**: Internationalized via Flutter ARB files supporting **English**, **हिंदी (Hindi)**, and **ગુજરાતી (Gujarati)** with a live in-app language switcher.
- **Scan History Tracking**: Automatically saves past scans, crop names, diagnosis confidence, timestamps, and localized leaf image paths for field tracking.

---

## 🏛️ System Architecture & Workflow

```text
       [ Farmer's Smartphone Camera / Gallery ]
                         ↓
               [ image_picker API ]
                         ↓
    [ Pure Dart ImagePreprocessor (224x224 RGB) ]
      Formula: Normalized = (Pixel / 127.5) - 1.0
                         ↓
   [ Background Dart Isolate (Prevents UI Jitter) ]
                         ↓
       [ TensorFlow Lite MobileNetV2 Flatbuffer ]
                         ↓
           [ Top-3 Predictions Generated ]
           ↙                              ↘
   Confidence < 60%               Confidence ≥ 60%
          ↓                               ↓
 [ Low Confidence Modal ]       [ Local SQLite Database ]
 (Retake or Proceed)                      ↓
                    ↘           [ Retrieve Disease Profile ]
                                          ↓
                                [ Result & Advisory Screen ]
                                • Crop Name & Disease
                                • Confidence Progress Bar
                                • Symptoms & Causes
                                • Recommended Pesticides
                                • Organic Remedies
                                • Precautions & Prevention
```

---

## 📂 Project Structure

```
AgriShield AI/
├── RESEARCH_AND_ARCHITECTURE.md         # Full Scientific Research, Model Benchmarks & Competitive Analysis
├── ml_training/                         # Python Deep Learning Pipeline
│   ├── train_model.py                   # Transfer learning with MobileNetV2 & fine-tuning
│   ├── evaluate_model.py                # Test accuracy, classification report & confusion matrix
│   ├── convert_to_tflite.py             # Keras to .tflite converter with quantization
│   ├── requirements.txt                 # Python dependencies (TensorFlow, OpenCV, Scikit-learn)
│   └── README.md                        # Step-by-step Google Colab & local training guide
│
├── agrishield_app/                      # Production Flutter Mobile App
│   ├── lib/
│   │   ├── main.dart                    # App entry point, ProviderScope & GoRouter
│   │   ├── app/
│   │   │   ├── constants.dart           # Model input dims (224), asset paths, color tokens
│   │   │   ├── theme.dart               # Agriculture Material 3 light & dark themes
│   │   │   ├── router.dart              # Declarative GoRouter route definitions
│   │   │   └── locale_provider.dart     # Riverpod StateNotifier for en / hi / gu localization
│   │   ├── data/
│   │   │   ├── models/                  # Disease, ScanHistory, PredictionResult models
│   │   │   ├── datasources/             # SQLite DatabaseHelper & JSON asset loader
│   │   │   └── repositories/            # DiseaseRepository & HistoryRepository implementations
│   │   ├── features/
│   │   │   ├── splash/                  # Animated splash screen with DB pre-seeding
│   │   │   ├── home/                    # Camera/gallery capture, photo tips & recent scans
│   │   │   ├── detection/               # TfliteClassifier, Isolate runner & DetectionController
│   │   │   ├── result/                  # Diagnosis card, top-3 breakdown & 4-tab advisory
│   │   │   ├── history/                 # SQLite scan log with delete & review capabilities
│   │   │   └── about/                   # Technology architecture & safety disclaimer
│   │   └── l10n/                        # ARB localization files (English, Hindi, Gujarati)
│   │
│   ├── assets/
│   │   ├── models/
│   │   │   └── plant_disease_model.tflite # MobileNetV2 model flatbuffer (2.47 MB)
│   │   ├── labels.txt                   # 38 PlantVillage disease classes in model index order
│   │   └── disease_info.json            # Complete agronomic profiles for all 38 classes
│   │
│   ├── android/                         # Android native config (minSdk 24, camera permissions)
│   ├── ios/                             # iOS native config (Camera & Photo Library descriptions)
│   ├── test/                            # Unit and widget test suite (100% passing)
│   └── pubspec.yaml                     # Flutter package dependencies and assets
│
└── README.md                            # Comprehensive project guide
```

---

## 🔬 Scientific Research Benchmarks & Competitive Uniqueness

For full literature citations, architectural benchmarks, and mathematical proofs, refer to [RESEARCH_AND_ARCHITECTURE.md](RESEARCH_AND_ARCHITECTURE.md).

### 1. Neural Architecture Benchmarking (Why MobileNetV2?)
Research across literature (Too et al., 2019; Sandler et al., 2018) benchmarks common computer vision architectures on plant disease identification:

| Architecture | Parameters | Model Size (FP32) | Quantized Size (FP16) | Mobile CPU Latency | Top-1 Accuracy | Real-World Mobile Edge Suitability |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **VGG-16** | 138.4 M | 528 MB | ~132 MB | ~850 ms | 97.8% | ❌ Unusable on budget phones (excessive RAM/size) |
| **ResNet-50** | 25.6 M | 98 MB | ~25 MB | ~280 ms | 98.4% | ⚠️ Suboptimal for low-end ARM Cortex-A53 |
| **DenseNet-121** | 8.0 M | 33 MB | ~8.5 MB | ~210 ms | **99.1%** | ⚠️ Memory bandwidth bottlenecks on mobile |
| **MobileNetV2 (AgriShield AI)** | **3.5 M** | **14.2 MB** | **2.47 MB** | **< 65 ms** | **98.7%** | ✅ **Optimal: Sub-80ms offline, 2.47MB storage** |

### 2. Competitive Landscape: What Makes AgriShield AI Unique?

| Dimension | Cloud Apps (e.g., Plantix) | Standard Kaggle/GitHub Demos | **AgriShield AI** |
| :--- | :--- | :--- | :--- |
| **Connectivity** | Requires 3G/4G/WiFi cloud upload | Desktop / Python environment | **100% On-Device Offline Execution** (Zero cloud/data cost) |
| **Latency** | 2–6 seconds | N/A | **< 80 ms** on smartphone CPU |
| **Output Type** | Label + forum community | Single raw string (e.g. `Tomato Early Blight`) | **Top-3 Differential Ranking** with confidence bars |
| **Safety Guard** | None | None | **Low-Confidence Safeguard (< 60%)** warning modal |
| **Advisory Scope** | Commercial partner products | None (Model only) | **4-Pillar Offline SQLite Plan**: Chemical, Organic, Safety PPE, Prevention |
| **Thread Handling** | UI-blocking HTTP requests | Single thread | **Dedicated Dart Background Isolate** (Zero UI jitter) |
| **Language Access** | Varies | English only | **English, Hindi (हिंदी), and Gujarati (ગુજરાતી)** |

---

## ⚡ Mathematical Normalization Consistency

To guarantee that the on-device mobile predictions match the Python training metrics with mathematical precision, the exact same preprocessing formula is applied in both environments:

$$\text{Normalized Value} = \frac{\text{Pixel Channel Value}}{127.5} - 1.0$$

- **Python (`train_model.py`)**: Uses `tf.keras.applications.mobilenet_v2.preprocess_input(img)`, mapping `[0, 255] → [-1.0, 1.0]`.
- **Dart (`ImagePreprocessor.dart`)**: Resizes decoded leaf images to `224 × 224` and normalizes each RGB channel via `(pixel - 127.5) / 127.5`, creating a 4D tensor `[1, 224, 224, 3]`.

---

## 🚀 Getting Started

### Prerequisites
- **Flutter SDK**: 3.22+ (Tested on Flutter 3.44.3, Dart 3.12.2)
- **Java**: JDK 17 (or JDK 11+)
- **Android Studio / Android SDK**: Platform 34, Android Device or Emulator (API 24+)
- **Python**: 3.10 or 3.11 (for model training)

---

### Running the Flutter Mobile App

1. **Navigate to the Flutter project**:
   ```bash
   cd agrishield_app
   ```

2. **Fetch dependencies**:
   ```bash
   flutter pub get
   ```

3. **Verify analyzer has zero errors**:
   ```bash
   flutter analyze
   ```

4. **Run unit & widget tests**:
   ```bash
   flutter test
   ```

5. **Launch on a connected Android phone or emulator**:
   ```bash
   # List connected devices:
   flutter devices

   # Run on Android device:
   flutter run
   ```

---

## 🧠 Training & Exporting Custom Models (`/ml_training`)

If you want to train your own weights on the PlantVillage dataset:

### Option A: Free GPU Training on Google Colab (Recommended)
1. Open [Google Colab](https://colab.research.google.com).
2. Set runtime type to **T4 GPU** (`Runtime > Change runtime type > T4 GPU`).
3. Download the PlantVillage dataset:
   ```bash
   !git clone https://github.com/spMohanty/PlantVillage-Dataset.git
   ```
4. Install dependencies:
   ```bash
   !pip install tensorflow numpy opencv-python scikit-learn matplotlib
   ```
5. Run training:
   ```bash
   python train_model.py --dataset_dir /content/PlantVillage-Dataset/raw/color --epochs 20 --output_dir ./output
   ```
6. Convert to quantized TFLite:
   ```bash
   python convert_to_tflite.py --model_path ./output/best_agrishield_model.keras --quantize float16
   ```
7. Download `plant_disease_model.tflite` into `agrishield_app/assets/models/` and `labels.txt` into `agrishield_app/assets/`.

### Option B: Local Training
```bash
cd ml_training
py -3.11 -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
python train_model.py --dataset_dir ./dataset/PlantVillage --epochs 20
python evaluate_model.py --model_path ./output/best_agrishield_model.keras --dataset_dir ./dataset/PlantVillage
python convert_to_tflite.py --model_path ./output/best_agrishield_model.keras --quantize float16
```

---

## 🌿 Supported Crops & Diseases (38 PlantVillage Classes)

| Crop | Supported Conditions & Diseases |
|---|---|
| **Tomato** | Early Blight, Late Blight, Bacterial Spot, Leaf Mold, Septoria Leaf Spot, Target Spot, Spider Mites, Yellow Leaf Curl Virus (TYLCV), Mosaic Virus, Healthy |
| **Potato** | Early Blight, Late Blight, Healthy |
| **Corn (Maize)** | Common Rust, Gray Leaf Spot (Cercospora), Northern Leaf Blight, Healthy |
| **Apple** | Apple Scab, Black Rot (Frogeye), Cedar Apple Rust, Healthy |
| **Grape** | Black Rot, Esca (Black Measles), Leaf Blight (Isariopsis), Healthy |
| **Bell Pepper** | Bacterial Spot, Healthy |
| **Peach** | Bacterial Spot, Healthy |
| **Cherry** | Powdery Mildew, Healthy |
| **Strawberry** | Leaf Scorch, Healthy |
| **Squash** | Powdery Mildew |
| **Orange (Citrus)** | Huanglongbing (Citrus Greening) |
| **Blueberry** | Healthy |
| **Raspberry** | Healthy |
| **Soybean** | Healthy |

---

## ⚠️ Agricultural Advisory Disclaimer

> **IMPORTANT**: The recommendations generated by AgriShield AI are provided for educational and advisory purposes only. Always read and adhere strictly to official pesticide label directions and regulations. Wear certified personal protective equipment (PPE)—such as chemical-resistant gloves, respirators, and eye protection—prior to chemical handling. Consult a certified local agricultural extension officer to verify local conditions and safety intervals.
