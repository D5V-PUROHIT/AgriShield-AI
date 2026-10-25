"""
AgriShield AI - Keras to TFLite Converter & Mobile Model Exporter
=================================================================
Converts a trained Keras plant disease model into an optimized TensorFlow Lite
(.tflite) flatbuffer for on-device mobile execution with tflite_flutter.

Features:
  1. Standard Float32 conversion
  2. Optional Dynamic Range / Float16 quantization (reduces model size from ~14MB to ~4MB)
  3. Verifies TFLite inference on dummy input (1, 224, 224, 3)
  4. Exports/syncs labels.txt and .tflite directly into Flutter assets directory
  5. Includes `--generate_placeholder_model` mode to generate a valid mobile TFLite flatbuffer
     for immediate app testing prior to full training.

Usage:
    python convert_to_tflite.py --model_path ./output/best_agrishield_model.keras --quantize float16
    python convert_to_tflite.py --generate_placeholder_model
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
    parser = argparse.ArgumentParser(description="Convert AgriShield AI Model to TFLite")
    parser.add_argument(
        "--model_path",
        type=str,
        default="./output/best_agrishield_model.keras",
        help="Path to trained Keras model",
    )
    parser.add_argument(
        "--labels_path",
        type=str,
        default="./output/labels.txt",
        help="Path to class labels.txt",
    )
    parser.add_argument(
        "--output_dir",
        type=str,
        default="./output",
        help="Output directory for .tflite model",
    )
    parser.add_argument(
        "--quantize",
        type=str,
        choices=["none", "dynamic", "float16"],
        default="none",
        help="Quantization optimization type",
    )
    parser.add_argument(
        "--copy_to_flutter",
        action="store_true",
        default=True,
        help="Copy resulting .tflite and labels.txt into Flutter assets folder",
    )
    parser.add_argument(
        "--generate_placeholder_model",
        action="store_true",
        help="Generate a valid MobileNetV2 architecture TFLite model for immediate app testing without waiting for training",
    )
    return parser.parse_args()


def generate_placeholder_tflite(output_path: str, labels_path: str):
    """
    Constructs a MobileNetV2-based model initialized with ImageNet features and a 38-class head,
    converting it into a real, functional TFLite flatbuffer.
    This ensures `tflite_flutter` in the app can load and run real inference on devices immediately.
    """
    print("[*] Generating MobileNetV2 mobile model flatbuffer for AgriShield AI...")
    num_classes = len(PLANT_VILLAGE_38_CLASSES)

    inputs = tf.keras.Input(shape=(224, 224, 3), name="input_1")
    # MobileNetV2 preprocessing: (x / 127.5) - 1.0
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


def convert_model(model_path: str, output_path: str, quantize: str):
    """Converts Keras model to TFLite format with selected optimizations."""
    print(f"[1/3] Loading Keras model: {model_path}")
    model = tf.keras.models.load_model(model_path)

    print(f"[2/3] Converting to TFLite (quantization={quantize})...")
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


def verify_tflite_model(tflite_path: str):
    """Verifies that the TFLite model can be executed with interpreter."""
    print(f"[3/3] Verifying TFLite model execution...")
    interpreter = tf.lite.Interpreter(model_path=tflite_path)
    interpreter.allocate_tensors()

    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    print(f"      Input Tensor: shape={input_details[0]['shape']}, dtype={input_details[0]['dtype']}")
    print(f"      Output Tensor: shape={output_details[0]['shape']}, dtype={output_details[0]['dtype']}")

    # Run dummy test inference
    dummy_input = np.random.uniform(0.0, 255.0, size=(1, 224, 224, 3)).astype(np.float32)
    # Check whether model input requires float32 or uint8
    if input_details[0]["dtype"] == np.float32:
        interpreter.set_tensor(input_details[0]["index"], dummy_input)
    else:
        interpreter.set_tensor(input_details[0]["index"], dummy_input.astype(np.uint8))

    interpreter.invoke()
    preds = interpreter.get_tensor(output_details[0]["index"])
    top_class = np.argmax(preds[0])
    print(f"      Test Inference Output Shape: {preds.shape}")
    print(f"      Test Top Class Index: {top_class} with probability: {preds[0][top_class]:.4f}")
    print("[OK] TFLite model verified successfully!")


def sync_to_flutter(tflite_path: str, labels_path: str):
    """Copies TFLite and labels.txt to Flutter app assets directory."""
    flutter_assets_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "agrishield_app", "assets"))
    models_dir = os.path.join(flutter_assets_dir, "models")
    os.makedirs(models_dir, exist_ok=True)

    dest_model = os.path.join(models_dir, "plant_disease_model.tflite")
    dest_labels = os.path.join(flutter_assets_dir, "labels.txt")

    if os.path.exists(tflite_path):
        shutil.copyfile(tflite_path, dest_model)
        print(f"[Flutter Sync] Copied model to: {dest_model}")

    if os.path.exists(labels_path):
        shutil.copyfile(labels_path, dest_labels)
        print(f"[Flutter Sync] Copied labels to: {dest_labels}")


def main():
    args = parse_arguments()
    os.makedirs(args.output_dir, exist_ok=True)
    tflite_output_path = os.path.join(args.output_dir, "plant_disease_model.tflite")
    labels_output_path = os.path.join(args.output_dir, "labels.txt")

    if args.generate_placeholder_model:
        generate_placeholder_tflite(tflite_output_path, labels_output_path)
    else:
        if not os.path.exists(args.model_path):
            print(f"[!] Model not found at '{args.model_path}'.")
            print("    Switching to placeholder model generation for immediate mobile app testing...")
            generate_placeholder_tflite(tflite_output_path, labels_output_path)
        else:
            convert_model(args.model_path, tflite_output_path, args.quantize)
            if not os.path.exists(args.labels_path):
                # Write standard labels
                with open(labels_output_path, "w", encoding="utf-8") as f:
                    for c in PLANT_VILLAGE_38_CLASSES:
                        f.write(f"{c}\n")
            else:
                shutil.copyfile(args.labels_path, labels_output_path)

    verify_tflite_model(tflite_output_path)

    if args.copy_to_flutter:
        sync_to_flutter(tflite_output_path, labels_output_path)


if __name__ == "__main__":
    main()
