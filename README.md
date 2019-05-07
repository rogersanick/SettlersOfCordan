<p align="center">
  <img src="https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png" alt="Corda" width="500">
</p>

# Settlers Of CorDan

Welcome to the Settlers of CorDan! This is a fully distributed board game, based on the classic 'Settlers of Catan.' Funny enough, board games are an excellent example of why 
we need Distributed Ledger Technology (DLT) and some of its advantages over centralized systems. 

## How do you build a board game on DLT?

In a non-digital, real-life board game, players use symbolic tokens and intricate game boards to maintain a shared understanding of the state of a given game. 
They update this shared representation of the game by first announcing their intent, verifying that the move is valid with their counterparties (based on the previously agreed 
upon rules), and then imparting that change by making an update. This update could include any number of actions depending on the game, moving a tile, advancing a piece, 
flipping over a card. The action or vehicle of delivery is irrelevant, but it's payload and impact are the same - with the consent of the counterparties, the player has changed our 
shared understanding of the game. They have persisted new information to our shared understanding. That understanding them forms the basis for future updates going forward. 
Players continue this cycle of proposing updates, verifying updates and making updates until the game is won.

Corda is a DLT platform that enables mutually distrusting parties to maintain consensus over a set of shared facts. In this implementation we use Corda states, contracts and flows
to enable all Settlers of CorDan players to participate in that same cycle: propose --> verify --> persist. 

## Why build a board game on DLT?

So why DLT? The short answer is that we eliminate the opportunity for cheating. There are two kinds of cheating we are trying to solution for:

- Malicious changes to the HISTORY of the game state.
- Malicious updates to the current game state.

Imagine you are playing a digital game of Catan in a traditional, centralized architecture. You and all counterparties (opposing players) are both accessing a front-end, which 
communicates with a webserver, hosted by a cloud-provider. The hosted webserver then makes updates to a hosted DB in order to persist the current version of the game state.

There's nothing inherently villanous about this architecture, our issue however, stems from the fact that we cannot be 100% certain that the hosting party has not impacted 
the current state of a given game. What if this was a professional match of Catan and we had millions of dollars on the line? A centralized architecture means that we are 
completely reliant on the honesty and ability of the hosting party to maintain our source of truth.

If the hosting party does make a malicious or even erroneous update to the DB - we will have no recourse. It is the board game equivalent of all players describing the actions
or moves they wish to make in a game to an unknown third party - who is updating a boardgame in another room. 

So we propose a decentalized architecture! 

# Pre-Requisites

See https://docs.corda.net/getting-set-up.html.

# Usage

## Running the nodes

See https://docs.corda.net/tutorial-cordapp.html#running-the-example-cordapp.

## Interacting with the nodes

### Shell

When started via the command line, each node will display an interactive shell:

    Welcome to the Corda interactive shell.
    Useful commands include 'help' to see what is available, and 'bye' to shut down the node.
    
    Tue Nov 06 11:58:13 GMT 2018>>>

You can use this shell to interact with your node. For example, enter `run networkMapSnapshot` to see a list of 
the other nodes on the network:

    Tue Nov 06 11:58:13 GMT 2018>>> run networkMapSnapshot
    [
      {
      "addresses" : [ "localhost:10002" ],
      "legalIdentitiesAndCerts" : [ "O=Notary, L=London, C=GB" ],
      "platformVersion" : 3,
      "serial" : 1541505484825
    },
      {
      "addresses" : [ "localhost:10005" ],
      "legalIdentitiesAndCerts" : [ "O=PartyA, L=London, C=GB" ],
      "platformVersion" : 3,
      "serial" : 1541505382560
    },
      {
      "addresses" : [ "localhost:10008" ],
      "legalIdentitiesAndCerts" : [ "O=PartyB, L=New York, C=US" ],
      "platformVersion" : 3,
      "serial" : 1541505384742
    }
    ]
    
    Tue Nov 06 12:30:11 GMT 2018>>> 

You can find out more about the node shell [here](https://docs.corda.net/shell.html).

### Client

`clients/src/main/kotlin/com/template/Client.kt` defines a simple command-line client that connects to a node via RPC 
and prints a list of the other nodes on the network.

#### Running the client

##### Via the command line

Run the `runTemplateClient` Gradle task. By default, it connects to the node with RPC address `localhost:10006` with 
the username `user1` and the password `test`.

##### Via IntelliJ

Run the `Run Template Client` run configuration. By default, it connects to the node with RPC address `localhost:10006` 
with the username `user1` and the password `test`.

### Webserver

`clients/src/main/kotlin/com/template/webserver/` defines a simple Spring webserver that connects to a node via RPC and 
allows you to interact with the node over HTTP.

The API endpoints are defined here:

     clients/src/main/kotlin/com/template/webserver/Controller.kt

And a static webpage is defined here:

     clients/src/main/resources/static/

#### Running the webserver

##### Via the command line

Run the `runTemplateServer` Gradle task. By default, it connects to the node with RPC address `localhost:10006` with 
the username `user1` and the password `test`, and serves the webserver on port `localhost:10050`.

##### Via IntelliJ

Run the `Run Template Server` run configuration. By default, it connects to the node with RPC address `localhost:10006` 
with the username `user1` and the password `test`, and serves the webserver on port `localhost:10050`.

#### Interacting with the webserver

The static webpage is served on:

    http://localhost:10050

While the sole template endpoint is served on:

    http://localhost:10050/templateendpoint
    
# Extending the template

You should extend this template as follows:

* Add your own state and contract definitions under `contracts/src/main/kotlin/`
* Add your own flow definitions under `workflows/src/main/kotlin/`
* Extend or replace the client and webserver under `clients/src/main/kotlin/`

For a guided example of how to extend this template, see the Hello, World! tutorial 
[here](https://docs.corda.net/hello-world-introduction.html).
