![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# Settlers Of Cordan

This repo contains the source code required to deploy a Corda Node and play settlers of Catan on the blockchain.

# Setup

### Tools 
* JDK 1.8 latest version
* IntelliJ latest version (2017.1 or newer)
* git

After installing the required tools, clone or download a zip of this repository, and place it in your desired 
location.

### IntelliJ setup
* From the main menu, click `open` (not `import`!) then navigate to where you placed this repository.
* Click `File->Project Structure`, and set the `Project SDK` to be the JDK you downloaded (by clicking `new` and 
nagivating to where the JDK was installed). Click `Okay`.
* Next, click `import` on the `Import Gradle Project` popup, leaving all options as they are. 
* If you do not see the popup: Navigate back to `Project Structure->Modules`, clicking the `+ -> Import` button,
navigate to and select the repository folder, select `Gradle` from the next menu, and finally click `Okay`, 
again leaving all options as they are.

# Instructions

TBD

### Running the tests

Current tests should be run individually. Don't forget to specify the javaagent.

TBD - will likely be as follows. 

* Kotlin: Select `Kotlin - Unit tests` from the dropdown run configuration menu, and click the green play button.
* Java: Select `Java - Unit tests` from the dropdown run configuration menu, and click the green play button.

# Running the CorDapp
TBD

* Terminal: Navigate to the root project folder and run `./gradlew kotlin-source:deployNodes`, followed by 
`./kotlin-source/build/node/runnodes`

### Interacting with the CorDapp

TBD

## Troubleshooting:
When running the flow tests, if you get a Quasar instrumention error then add:

```-ea -javaagent:lib/quasar.jar```

to the VM args property in the default run configuration for JUnit in IntelliJ.

Solutions are available [here](https://github.com/corda/corda-training-solutions).