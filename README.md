# Intro

In this course we will be building pieces of an Accounts microservice for a Reactive Bank.

We will start with an initial state that has portions of the microservice complete.

Our job will be to complete the missing pieces, and modify them over time to try out various features of Akka Cluster Sharding.

# Existing Code

## Applications

There are 3 main application entry points for this project. Each application entry point includes one or more helper scripts to run that entry point. These scripts require you to work in an environment that can execute Bash scripts. However, these helper scripts simply wrap SBT commands, so if you aren't in an environment that supports Bash then you can run the SBT commands directly (or write your own helper scripts).

### Accounts Service

The entrypoint for the Accounts Service is in `Main.scala`. It pulls it's configuration from `resources/application.conf`.

There is a series of `runNodeX.sh` scripts where X is either 1, 2, or 3. These scripts run the microservice and apply a series of port overrides. The commands including the port overrides can be quite long. This simply allows you to run them more quickly without having to write out all of the overrides.

You can interact with the Accounts Service using the `accounts.sh` script.

To open an account run the following command, substituting a account holder name for <account holder>:

`./accounts.sh "open <account holder>"`

To find an account run the following command, substituting an appropriate Account Number for <account number>:

`./accounts.sh "find <account number>"`

To credit an amount to the account, run the following command, substituting the Account Number for <accountNumber> the amount <amount>:

`./accounts.sh "add <accounNumber> <amount>`

### Load Test

The entrypoint for the Load Test can be found in `LoadTest.scala`. It pulls it's configuration from `resources/loadtest.conf`

It simulates a collection of users going through a predefined set of steps (opening an account, retrieving the account, crediting amount to the account).

The load test assumes you are running all 3 instances of the application. If you want to change that you can override the ports in `loadtest.conf`

There is a `runLoadTest.sh` script to execute the Load Test. 

*NOTE:* The load test will push your system fairly hard. Keep that in mind if you are running off a laptop battery.

### Client

There is a small client application that can be found in `Client.scala`. It pulls it's configuration from `resources/client.conf`.

The client application allows you to make HTTP requests to the service using a simplified API (rather than sending full JSON requests). 

Alternatively you can use your favorite HTTP client (postman, curl etc). In that case you will have to understand the structure of the HTTP calls including their JSON payloads.

You can run the client application using `accounts.sh`.
