# Overview

## Design Process
After reading the requirements I drew out the user flow on a white board to get a better understanding of how to build the app. 

![User Flow](UserFlow.jpg)

I decided to create four very simple screens instead of having one screen that does everything, in order to follow a single responsibility design. Separating out the screens into fragments worked out nicely in that the view logic was concise and directly related to the task at hand. Shortly after getting familiar with the Twilio SDK I realized that much of the logic would be reused in both sharing and viewing a screen, along with relaying audio and touch events. Because of this, I abstracted the SDK logic into its own class and injected it into the fragments with Koin. This was great as it allows for code reuse and separation of concerns, which will make testing easier.

## Known Issues
On the main screen the user has two choices, "Share Screen", or "Join Screen". This is nice because each button takes the user to a very specific screen, and does exactly one thing, which is easy for the user to understand. However, this added more complexity in that validation is needed to check if a user is already sharing or viewing in the same room before another user selects the same option. Unfortunately, I didn't have enough time to handle this case so two users can select the same option in the same room. 

I also found toward the end of the project that a Service would have been a better choice to handle screen sharing and touch events instead of an Activity. This is mainly because a Service would have a higher priority to keep the app process alive in memory while the Activity is backgrounded by the user.

# Setup

## Access Tokens
To ensure the access tokens are retrieved correctly, make sure to use ngrok to setup a public http endpoint as specified  [in these instructions](https://github.com/twilio/video-quickstart-android#setup-an-access-token-server). Once the server is setup, place the access token server URL in the local.properties file.

> TWILIO_ACCESS_TOKEN_SERVER=\<server  URL\>

Similar to how to setup token retrieval configuration for the    [quickstartKotlin](https://github.com/twilio/video-quickstart-android/tree/master/quickstartKotlin) example project.

## Build & Run
Simply open this project in AndroidStudio (open project build.gradle) and run the app module run configuration to deploy the apk on two Android devices. There are no special build types or flavors, just select the debug build type. **NOTE**: After logging into the app, select "Share Screen" on one device and "Join Screen" on the other. As mentioned in the known issues section above, both devices should not select the same option.
