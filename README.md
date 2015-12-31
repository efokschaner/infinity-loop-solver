# infinity-loop-solver
Automatically plays the android game named "∞ Loop" (com.balysv.loop)

## How To
### Install the game if needed:

    adb install ∞\ Loop_v3_0.apk

### Install the application (debug works fine):

    ./gradlew installDebug

### Launch the application:

    adb shell am instrument -w efokschaner.infinityloopsolver/efokschaner.infinityloopsolver.SolverInstrumentation

The application only works when launched this way as it uses UiAutomation api's that are not
available to normally launched applications.
