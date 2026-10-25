# AgriShield AI - Native Android Application

AgriShield AI is an offline deep learning crop diagnosis and advisory Android application built with Native Android (Java + Material Design 3 XML layouts).

## Features
- **On-Device Offline Inference**: Pre-trained TensorFlow Lite model (`plant_disease_model.tflite`) classifies 38 crop-disease pairs without needing internet.
- **SQLite Disease Database**: Offline database storing disease symptoms, causes, chemical pesticides with dosage, organic remedies, precautions, and prevention tips.
- **Multilingual Support**: Supports English, Hindi (हिंदी), and Gujarati (ગુજરાતી) with in-app language switching.
- **Scan History**: Save and manage past scan records with persistent thumbnails and diagnostic summaries.

## Opening in Android Studio
1. Open Android Studio.
2. Select **Open** and choose the `agrishield_app/android` directory.
3. Ensure JDK 17 is configured under **Settings > Build, Execution, Deployment > Build Tools > Gradle**.
4. Sync Gradle and click **Run**.
