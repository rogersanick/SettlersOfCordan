<p align="center">
  <img src="https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png" alt="Corda" width="500">
</p>

# Settlers Of CorDan

Welcome to the Settlers of CorDan! This is a fully distributed board game, based on the classic 'Settlers of Catan.' Funny enough, board games are an excellent example of why 
we need Distributed Ledger Technology (DLT) and some of its advantages over centralized systems. 

## How does a decentralized board game work?

In a non-digital, real-life board game, players use symbolic tokens and intricate game boards to maintain a shared understanding of the state of a given game. 
They update this shared representation of the game by first announcing their intent, verifying that the move is valid with the other players (based on the previously agreed 
upon rules), and then imparting that change by making a physical update on the game board. This update could include any number of actions depending on the game, moving a tile, advancing a piece, 
flipping over a card. The action or vehicle of delivery is irrelevant, but it's payload and impact are the same - with the consent of the counterparties, the player has changed our 
shared understanding of the game - see where we're going here? 

They have persisted new information to each of our perspectives of the ledger. That shared understanding then forms the basis for future updates. Players continue this cycle of proposing updates, 
verifying these proposals and making updates until the game is won.

Corda is a DLT platform that enables mutually distrusting parties to maintain consensus over a set of shared facts. They do so by proposing valid updates to their peers, peers (fellow Corda nodes) 
verify these transactions and finally all relevant nodes persist new information to their respective ledgers. Notice the similarities to a board game? In a decentralized board game built on Corda, 
the set of shared facts is comprised of all of the relevant information of a board game! In this implementation we use Corda states, contracts and flows to model Settlers of Catan.

## Why build a board game on DLT?

So why DLT? The short answer is that we eliminate the opportunity for cheating.

Imagine you are playing a digital game of Catan in a traditional, centralized architecture. You and all counterparties (opposing players) are both accessing a front-end, which 
communicates with a webserver, hosted by a cloud-provider. The hosted webserver then makes updates to a hosted DB in order to persist the current version of the game state.

There's nothing inherently villanous about this architecture, our issue however, stems from the fact that we cannot be 100% certain that the hosting party has not impacted 
the state of a given game. What if this was a professional match of Catan and we had millions of dollars on the line? A centralized architecture means that we are 
completely reliant on the honesty and ability of the hosting party to maintain our source of truth.

If the hosting party does make a malicious or even erroneous update to the DB, changing the history of moves made in the board game - we will have no recourse. To use a real-world 
example, imagine all players of a board game describing their actions, or moves they wish to make, to an unknown third party - who is updating a boardgame in another room. If they 
suddenly decide the playerA has lost all their money, they will be able to make that change with no consequences (besides a very angry PartyA). The solution here, is to have PartyA
maintain their own copy of the board game or their own copy of a ledger with all information relevant to them. In fact, all players should keep their own copy of the board game
which effectively, will now act as a distributed ledger. This is what's happening under the hood of this CorDapp!

# Usage

## Running the nodes

Clone the repo from github:

    git clone https://github.com/rogersanick/SettlersOfCordan
    
Run the deploy nodes Gradle script:

    ./gradlew clean deployNodes
    
Run the following to deploy all nodes locally (four players, one notary and one dice-roll oracle):

    build/nodes/runNodes

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
      "addresses" : [ "localhost:10008" ],
      "legalIdentitiesAndCerts" : [ "O=PartyB, L=New York, C=US" ],
      "platformVersion" : 3,
      "serial" : 1541505384742
    }
    ]
    
    Tue Nov 06 12:30:11 GMT 2018>>> 

To start a game between all of the nodes running locally, use the following command.
    
    flow start SetupGameBoardFlow p1: PartyA, p2: PartyB, p3: PartyC, p4: PartyD

### Running the tests

##### Via IntelliJ

Run the `Run All Tests` run configuration in Intellij by selecting the configuration from the drop down in the 
top right of the application and then clicking the green play button.