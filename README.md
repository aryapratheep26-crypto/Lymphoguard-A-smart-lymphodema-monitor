# LymphoGuard – Smart Lymphedema Monitoring System

## 1. Project Overview

LymphoGuard is a wearable-based monitoring system designed to help users observe changes in swelling associated with lymphedema.

The system uses an ESP32 microcontroller and an FSR sensor to collect sensor readings. The readings can be transmitted to an Android application through Bluetooth, where users can view measurements, monitor trends, and review previous readings.

**Note:** LymphoGuard is a monitoring prototype and is not a substitute for professional medical diagnosis.

## 2. Problem Statement

Lymphedema causes swelling due to the accumulation of lymphatic fluid. Regularly monitoring changes in swelling can be challenging for people who need ongoing observation.

LymphoGuard aims to make monitoring more convenient through a wearable device connected to a mobile application.

## 3. Technologies Used

- Android Studio
- Kotlin
- Jetpack Compose
- ESP32 microcontroller
- FSR sensor
- Bluetooth communication
- Room Database
- Gradle

## 4. System Architecture

1. The FSR sensor collects pressure-related readings.
2. The ESP32 processes the sensor input.
3. Sensor readings are transmitted to the Android application through Bluetooth.
4. The application displays readings and monitoring status.
5. Room Database stores readings locally for reviewing previous measurements.

## 5. Requirements

- Android Studio
- Android device running Android 7.0 or later
- ESP32 and FSR sensor hardware for hardware-based testing
- USB cable for programming the ESP32
- Bluetooth enabled on the Android device

The application can be built in Android Studio. Hardware-based functionality requires the compatible device and sensor setup.

## 6. Setup Instructions

### Step 1: Clone the repository

Open a terminal and run:

```bash
git clone https://github.com/aryapratheep26-crypto/Lymphoguard-A-smart-lymphodema-monitor.git
```

### Step 2: Open the project

1. Open Android Studio.
2. Select **Open**.
3. Choose the downloaded LymphoGuard project folder.
4. Allow Gradle to sync and download the required dependencies.

### Step 3: Configure the Android device

1. Connect an Android phone or start an emulator.
2. Enable Bluetooth on the phone when testing Bluetooth functionality.
3. Grant the required Bluetooth permissions when prompted.

### Step 4: Run the application

1. Select the application configuration in Android Studio.
2. Choose the connected Android device.
3. Click **Run**.
4. Follow the application screens to view the available monitoring features.

### Step 5: Test the hardware connection

1. Connect the FSR sensor to the ESP32 according to the hardware circuit.
2. Upload the compatible ESP32 firmware.
3. Power on the hardware.
4. Pair or connect the device using the application's supported Bluetooth workflow.
5. Verify that readings are received and displayed correctly.

The hardware firmware and sensor configuration must match the implementation used in the prototype.

## 7. Configuration Variables

The project uses Android and Gradle configuration files to manage application settings and dependencies.

| Configuration | Purpose |
|---|---|
| applicationId | Identifies the Android application |
| minSdk | Specifies the minimum supported Android version |
| compileSdk | Specifies the Android API version used to compile the application |
| targetSdk | Specifies the Android API level the application targets |
| Bluetooth permissions | Support Bluetooth discovery and communication |
| Database name | Identifies the local Room Database |

Check the project's Gradle files and AndroidManifest.xml for the actual configured values.

No external API keys or server environment variables are required unless additional services are configured.

## 8. Data Storage

The application uses a local Room Database to store monitoring readings.

The readings table contains fields such as:

- Reading ID
- FSR sensor value
- Circumference measurement
- Monitoring status
- Date
- Time

## 9. Testing

The prototype can be tested by:

1. Building the application successfully in Android Studio.
2. Checking Bluetooth connectivity.
3. Verifying sensor data reception.
4. Checking whether readings appear correctly in the application.
5. Confirming that readings are stored and can be reviewed.

Actual test results should be recorded after testing the prototype.

## 10. Limitations

- Sensor accuracy depends on calibration and hardware configuration.
- The prototype requires testing to establish measurement reliability.
- It is not a clinically validated diagnostic device.
- Medical decisions should be made with qualified healthcare professionals.

## 11. Future Improvements

- Improve sensor calibration and measurement reliability.
- Add more detailed monitoring trends.
- Improve alerts for changes in measurements.
- Conduct further testing with appropriate healthcare guidance.

## 12. License

See the LICENSE file in this repository for licensing information.
