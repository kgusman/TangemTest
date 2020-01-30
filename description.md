# Tangem cards

## Demo App scenario

For working with demo app you need three Tangem cards - 2 for user and 1 for manager. Set public keys of these cards in Iroha `genesis.block`. You can sign user transactions with these two cards (any of them). Quorum for the user is set to 1.

And also update public key of the user in the code (which will be removed from signatories)

## Sign Transaction

1. Press `Scan` button to read information about the user card
2. Scan user card
3. Press `Sign`
4. Scan card and hold it till countdown ends
5. Wait for `Transaction has been sent` message

## Remove Signatory (card)

1. Press `Scan` button
2. Scan user card which public key is not in the code of this application
3. Press `Report about lost Card`
4. Scan card and hold it till countdown ends
5. Wait for `Transaction has been sent` message

## Add new signatory (card)

1. Press `Scan` button
2. Scan second user card
3. Press `Grant Permission`
4. Scan user card and hold it till countdown ends
5. Wait for `Transaction has been sent` message
6. Press `Scan` button
7. Scan manager card
8. Press `Add card to a user`
9. Scan manager card and hold it till countdown ends
10. Wait for `Transaction has been sent` message

Steps 1-5 can be skipped, if genesis block contains transaction that gives permissions for manager to add signatory to the user.

# Suggestions

## Lost card

If a user lost one of the provided cards, he can notify FI/NBC about that via application.

For example:

- User press' corresponding button in an application
- The application asks user to scan a second card
- After scanning, a public key can be extracted from the card and public key of the lost card can be deleted as Signatory from the user's account
- Application suggests to the user to come to the FI/NBC office and get a new additional card

Also, the public key of the lost card can be saved into some database and be monitored for an activity (if someone will try to do something bad).
