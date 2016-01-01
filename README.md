# infinity-loop-solver
Automatically plays the android game named "∞ Loop" (com.balysv.loop)

## How To
### Install the game if needed:

    adb install ∞\ Loop_v3_0.apk

### Install OpenCV Manager application:
If you have the Play Store on the device you can skip this step
as the application should (in theory) prompt you to install it.

Either get it from the Play Store or install it from the SDK
http://sourceforge.net/projects/opencvlibrary/files/opencv-android/
eg.

    adb install OpenCV-android-sdk/apk/OpenCV_3.1.0_Manager_3.10_x86.apk

Make sure to pick the right architecture apk for the target device

### Install the application (debug works fine):

    ./gradlew installDebug

### Launch the application:

    adb shell am instrument -w efokschaner.infinityloopsolver/efokschaner.infinityloopsolver.SolverInstrumentation

The application only works when launched this way as it uses UiAutomation api's that are not
available to normally launched applications.
