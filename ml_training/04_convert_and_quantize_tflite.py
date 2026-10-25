"""
AgriShield AI - Step 4: Model Quantization & TFLite Mobile Export
=================================================================
Role:
    Converts trained Keras model into an optimized TensorFlow Lite (.tflite) flatbuffer.
    Features:
      1. Float32 conversion & Post-Training Quantization (Float16, Dynamic Range)
      2. Model size reduction (from ~14MB down to ~3.9MB)
      3. Verifies TFLite execution with test inference tensor (1, 224, 224, 3)
      4. Automatically syncs .tflite weights and labels.txt into Android app assets folder:
         agrishield_app/android/app/src/main/assets/models/plant_disease_model.tflite
         agrishield_app/android/app/src/main/assets/labels.txt

Usage:
    python 04_convert_and_quantize_tflite.py --model_path ./output/best_agrishield_model.keras --quantize float16
    python 04_convert_and_quantize_tflite.py --generate_placeholder_model
"""

import os
import shutil
import argparse
import numpy as np
import tensorflow as tf

IMAGE_SIZE = (224, 224)
INPUT_SHAPE = (1, 224, 224, 3)

# 38 Standard PlantVillage Classes
PLANT_VILLAGE_38_CLASSES = [
    "Apple___Apple_scab",
    "Apple___Black_rot",
    "Apple___Cedar_apple_rust",
    "Apple___healthy",
    "Blueberry___healthy",
    "Cherry_(including_sour)___Powdery_mildew",
    "Cherry_(including_sour)___healthy",
    "Corn_(maize)___Cercospora_leaf_spot Gray_leaf_spot",
    "Corn_(maize)___Common_rust_",
    "Corn_(maize)___Northern_Leaf_Blight",
    "Corn_(maize)___healthy",
    "Grape___Black_rot",
    "Grape___Esca_(Black_Measles)",
    "Grape___Leaf_blight_(Isariopsis_Leaf_Spot)",
    "Grape___healthy",
    "Orange___Haunglongbing_(Citrus_greening)",
    "Peach___Bacterial_spot",
    "Peach___healthy",
    "Pepper,_bell___Bacterial_spot",
    "Pepper,_bell___healthy",
    "Potato___Early_blight",
    "Potato___Late_blight",
    "Potato___healthy",
    "Raspberry___healthy",
    "Soybean___healthy",
    "Squash___Powdery_mildew",
    "Strawberry___Leaf_scorch",
    "Strawberry___healthy",
    "Tomato___Bacterial_spot",
    "Tomato___Early_blight",
    "Tomato___Late_blight",
    "Tomato___Leaf_Mold",
    "Tomato___Septoria_leaf_spot",
    "Tomato___Spider_mites Two-spotted_spider_mite",
    "Tomato___Target_Spot",
    "Tomato___Tomato_Yellow_Leaf_Curl_Virus",
    "Tomato___Tomato_mosaic_virus",
    "Tomato___healthy",
]


def parse_arguments():
    parser = argparse.ArgumentParser(description="Convert & Quantize AgriShield AI Model to TFLite")
    parser.add_argument(
        "--model_path",
        type=str,
        default="./output/best_agrishield_model.keras",
        help="Path to trained .keras model",
    )
    parser.add_argument(
        "--output_dir",
        type=str,
        default="./output",
        help="Directory to save exported .tflite model and labels.txt",
    )
    parser.add_argument(
        "--quantize",
        type=str,
        choices=["none", "dynamic", "float16"],
        default="float16",
        help="Quantization technique: 'none' (FP32), 'dynamic', or 'float16'",
    )
    parser.add_argument(
        "--sync_to_android",
        action="store_true",
        default=True,
        help="Copy resulting .tflite and labels.txt into Android app assets folder",
    )
    parser.add_argument(
        "--generate_placeholder_model",
        action="store_true",
        help="Generate a valid MobileNetV2 architecture TFLite model for immediate app testing",
    )
    return parser.parse_args()


def convert_and_quantize(model_path: str, output_path: str, quantize: str):
    """Converts Keras model to TFLite format with selected optimizations."""
    print(f"[*] Loading Keras model: {model_path}")
    model = tf.keras.models.load_model(model_path)

    print(f"[*] Converting to TFLite (quantization={quantize})...")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)

    if quantize == "dynamic":
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
    elif quantize == "float16":
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_types = [tf.float16]

    tflite_model = converter.convert()

    with open(output_path, "wb") as f:
        f.write(tflite_model)

    size_mb = len(tflite_model) / (1024 * 1024)
    print(f"[OK] Saved TFLite model to: {output_path} ({size_mb:.2f} MB)")


def verify_tflite_inference(tflite_path: str):
    """Verifies that the TFLite model can execute with the TFLite Interpreter."""
    print(f"[*] Verifying TFLite model execution on dummy input tensor...")
    interpreter = tf.lite.Interpreter(model_path=tflite_path)
    interpreter.allocate_tensors()

    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    print(f"    Input Tensor:  shape={input_details[0]['shape']}, dtype={input_details[0]['dtype']}")
    print(f"    Output Tensor: shape={output_details[0]['shape']}, dtype={output_details[0]['dtype']}")

    # Run dummy test inference
    dummy_input = np.random.uniform(0.0, 255.0, size=(1, 224, 224, 3)).astype(np.float32)
    if input_details[0]["dtype"] == np.float32:
        interpreter.set_tensor(input_details[0]["index"], dummy_input)
    else:
        interpreter.set_tensor(input_details[0]["index"], dummy_input.astype(np.uint8))

    interpreter.invoke()
    preds = interpreter.get_tensor(output_details[0]["index"])
    top_class = np.argmax(preds[0])
    print(f"    Test Inference Output Shape: {preds.shape}")
    print(f"    Test Top Class Index: {top_class} (Probability: {preds[0][top_class]:.4f})")
    print("[OK] TFLite model verified successfully!")


def sync_assets_to_android(tflite_path: str, labels_path: str):
    """Copies TFLite model and labels.txt into the Android app assets directory."""
    android_assets_dir = os.path.abspath(
        os.path.join(os.path.dirname(__file__), "..", "agrishield_app", "android", "app", "src", "main", "assets")
    )
    models_dir = os.path.join(android_assets_dir, "models")
    os.makedirs(models_dir, exist_ok=True)

    dest_model = os.path.join(models_dir, "plant_disease_model.tflite")
    dest_labels = os.path.join(android_assets_dir, "labels.txt")

    if os.path.exists(tflite_path):
        shutil.copyfile(tflite_path, dest_model)
        print(f"[*] [Android Sync] Updated: {dest_model}")

    if os.path.exists(labels_path):
        shutil.copyfile(labels_path, dest_labels)
        print(f"[*] [Android Sync] Updated: {dest_labels}")


def generate_placeholder_tflite(output_path: str, labels_path: str):
    """Constructs a MobileNetV2 architecture TFLite model for immediate app verification."""
    print("[*] Generating MobileNetV2 mobile model flatbuffer for AgriShield AI...")
    num_classes = len(PLANT_VILLAGE_38_CLASSES)

    inputs = tf.keras.Input(shape=(224, 224, 3), name="input_1")
    x = tf.keras.layers.Lambda(lambda img: (img / 127.5) - 1.0, name="preprocess")(inputs)
    base = tf.keras.applications.MobileNetV2(
        input_shape=(224, 224, 3),
        include_top=False,
        weights="imagenet",
        pooling="avg",
    )
    x = base(x)
    outputs = tf.keras.layers.Dense(num_classes, activation="softmax", name="output_prob")(x)

    model = tf.keras.Model(inputs=inputs, outputs=outputs, name="AgriShield_MobileNetV2_Mobile")

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()

    with open(output_path, "wb") as f:
        f.write(tflite_model)

    with open(labels_path, "w", encoding="utf-8") as f:
        for c in PLANT_VILLAGE_38_CLASSES:
            f.write(f"{c}\n")

    print(f"[OK] Generated mobile TFLite model at: {output_path} ({len(tflite_model) / 1024 / 1024:.2f} MB)")
    print(f"[OK] Generated labels file at: {labels_path}")


def main():
    args = parse_arguments()
    os.makedirs(args.output_dir, exist_ok=True)

    tflite_output_path = os.path.join(args.output_dir, "plant_disease_model.tflite")
    labels_output_path = os.path.join(args.output_dir, "labels.txt")

    if args.generate_placeholder_model:
        generate_placeholder_tflite(tflite_output_path, labels_output_path)
    else:
        convert_and_quantize(args.model_path, tflite_output_path, args.quantize)

    verify_tflite_inference(tflite_output_path)

    if args.sync_to_android:
        sync_assets_to_android(tflite_output_path, labels_output_path)


if __name__ == "__main__":
    main()
