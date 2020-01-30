package com.example.testtangem

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.tangem.CardManager
import com.tangem.commands.Card
import com.tangem.tangem_sdk_new.DefaultCardManagerDelegate
import com.tangem.tangem_sdk_new.NfcLifecycleObserver
import com.tangem.tangem_sdk_new.nfc.NfcManager
import com.tangem.tasks.ScanEvent
import com.tangem.tasks.TaskEvent
import iroha.protocol.Primitive
import iroha.protocol.Queries
import iroha.protocol.TransactionOuterClass
import jp.co.soramitsu.iroha.java.*
import org.apache.xerces.impl.dv.xs.HexBinaryDV
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
import org.jetbrains.anko.uiThread
import org.spongycastle.util.encoders.Hex
import org.spongycastle.util.encoders.HexEncoder
import javax.xml.bind.DatatypeConverter
import javax.xml.bind.annotation.adapters.HexBinaryAdapter


private const val TAG = "Tangem test"

class MainActivity : AppCompatActivity() {

    private val managerAccountName = "manager_bank"
    private val userAccountName = "test"
    private val domain = "tangem"
    private val userAtDomain = "%s@%s".format(userAccountName, domain)
    private val managerAtDomain = "%s@%s".format(managerAccountName, domain)
    private var userPublicKey = ""

    private val nfcManager = NfcManager()
    private val cardManagerDelegate: DefaultCardManagerDelegate =
        DefaultCardManagerDelegate(nfcManager.reader)
    private val cardManager = CardManager(nfcManager.reader, cardManagerDelegate)

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        nfcManager.setCurrentActivity(this)
        cardManagerDelegate.activity = this
        lifecycle.addObserver(NfcLifecycleObserver(nfcManager))
        val addNewUserCardButton: Button = findViewById(R.id.addNewUserCardButton)!!
        val scanButton: Button = findViewById(R.id.scanButton)!!
        val addCardButton: Button = findViewById(R.id.addCardButton)!!
        val signButton: Button = findViewById(R.id.signButton)!!
        val lostCardButton: Button = findViewById(R.id.lostCardButton)!!
        val irohaIpAddressEditText: EditText = findViewById(R.id.ipAddress)
        val cardPublicKeyText: EditText = findViewById(R.id.publicKey)
        val savePublicKeyToRemoveButton: Button = findViewById(R.id.savePublicKey)
        var card: Card? = null

        addNewUserCardButton.setOnClickListener { _ ->
            if (irohaIpAddressEditText.text.toString().isEmpty()) {
                toast("Please, set Iroha node IP address")
                return@setOnClickListener
            } else if (card == null) {
                toast("Please, scan your card first")
                return@setOnClickListener
            } else if (userPublicKey.equals("")) {
                toast("Please, save public key to add")
                return@setOnClickListener
            }

            val unsignedTransaction = createAddSignatoryTransaction(userAtDomain, DatatypeConverter.parseHexBinary(userPublicKey))

            cardManager.sign(
                arrayOf(unsignedTransaction.payload()),
                card!!.cardId
            ) {
                when (it) {
                    is TaskEvent.Completion -> {
                        if (it.error != null) runOnUiThread {
                            Log.e(TAG, it.error!!.message ?: "Error occurred")
                            toast("Error occurred")
                        }
                    }
                    is TaskEvent.Event -> runOnUiThread {
                        val signature = formSignature(it.data.signature, card!!.walletPublicKey)
//                        val signature = formSignature(it.data.signature, DatatypeConverter.parseHexBinary("fdb6cd0431bb4ef8f3ee7c27d0417bfc95ed70b66bbf72ead7516539ccb540ef"))
                        val signedTransaction = unsignedTransaction.addSignature(signature).build()
                        addNewUserCardButton.isEnabled = false
                        sendTransactionToIroha(
                            irohaIpAddressEditText.text.toString(),
                            signedTransaction,
                            {
                                addNewUserCardButton.isEnabled = true
                                toast("Transaction has been sent")
                            },
                            {
                                addNewUserCardButton.isEnabled = true
                                toast("Cannot send transaction")
                            }
                        )
                    }
                }
            }
        }

        // For saving current session public keys in app
        addCardButton.setOnClickListener{_ ->
            if (card == null) {
                toast("Please, scan your card first")
                return@setOnClickListener
            } else if (irohaIpAddressEditText.text.toString().isEmpty()) {
                toast("Please, set Iroha node IP address")
                return@setOnClickListener
            }
            // Create tx
            val unsignedTransaction = createGrantPermissionTransaction(managerAtDomain)
            // Sign it
            cardManager.sign(
                arrayOf(unsignedTransaction.payload()),
                card!!.cardId
            ) {
                when (it) {
                    is TaskEvent.Completion -> {
                        if (it.error != null) runOnUiThread {
                            Log.e(TAG, it.error!!.message ?: "Error occurred")
                            toast("Error occurred")
                        }
                    }
                    is TaskEvent.Event -> runOnUiThread {
                        val signature = formSignature(it.data.signature, card!!.walletPublicKey)
                        val signedTx = unsignedTransaction.addSignature(signature).build()
                        addCardButton.isEnabled = false
                        sendTransactionToIroha(
                            irohaIpAddressEditText.text.toString(),
                            signedTx,
                            {
                                addCardButton.isEnabled = true
                                toast("Transaction has been sent")
                            },
                            {
                                addCardButton.isEnabled = true
                                toast("Cannot send transaction")
                            })
                    }
                }
            }
        }

        savePublicKeyToRemoveButton.setOnClickListener { _ ->
            if (card == null) {
                toast("Please, scan your card first")
                return@setOnClickListener
            }
            userPublicKey = Hex.toHexString(card!!.walletPublicKey)
            toast("Successfully saved public key to remove")
        }



        // First, we have to scan the card to get its id and public key
        scanButton.setOnClickListener { _ ->
            cardManager.scanCard { taskEvent ->
                when (taskEvent) {
                    is TaskEvent.Event -> {
                        when (taskEvent.data) {
                            is ScanEvent.OnReadEvent -> {
                                // Handle returned card data
                                card = (taskEvent.data as ScanEvent.OnReadEvent).card
                                cardPublicKeyText.setText(Hex.toHexString(card!!.walletPublicKey))
                            }
                        }
                    }
                }
            }
        }

        lostCardButton.setOnClickListener{_ ->
            if (irohaIpAddressEditText.text.toString().isEmpty()) {
                toast("Please, set Iroha node IP address")
                return@setOnClickListener
            } else if (userPublicKey.equals("")) {
                toast("Please, set public key to remove")
                return@setOnClickListener
            }


            val unsignedTransaction = createRemoveSignatoryTransaction(DatatypeConverter.parseHexBinary(userPublicKey))
            // Sign it
            cardManager.sign(
                arrayOf(unsignedTransaction.payload()),
                card!!.cardId
            ) {
                when (it) {
                    is TaskEvent.Completion -> {
                        if (it.error != null) runOnUiThread {
                            Log.e(TAG, it.error!!.message ?: "Error occurred")
                            toast("Error occurred")
                        }
                    }
                    is TaskEvent.Event -> runOnUiThread {
                        val signature = formSignature(it.data.signature, card!!.walletPublicKey)
                        val signedTx = unsignedTransaction.addSignature(signature).build()
                        lostCardButton.isEnabled = false
                        sendTransactionToIroha(
                            irohaIpAddressEditText.text.toString(),
                            signedTx,
                            {
                                lostCardButton.isEnabled = true
                                toast("Transaction has been sent")
                            },
                            {
                                lostCardButton.isEnabled = true
                                toast("Cannot send transaction")
                            })
                    }
                }
            }

        }

        // Then, we create and sign a transaction
        signButton.setOnClickListener { _ ->
            if (card == null) {
                toast("Please, scan your card first")
                return@setOnClickListener
            } else if (irohaIpAddressEditText.text.toString().isEmpty()) {
                toast("Please, set Iroha node IP address")
                return@setOnClickListener
            }
            // Create tx
            val unsignedTransaction = createTransaction()
            // Sign it
            cardManager.sign(
                arrayOf(unsignedTransaction.payload()),
                card!!.cardId
            ) {
                when (it) {
                    is TaskEvent.Completion -> {
                        if (it.error != null) runOnUiThread {
                            Log.e(TAG, it.error!!.message ?: "Error occurred")
                            toast("Error occurred")
                        }
                    }
                    is TaskEvent.Event -> runOnUiThread {
                        val signature = formSignature(it.data.signature, card!!.walletPublicKey)
                        val signedTx = unsignedTransaction.addSignature(signature).build()
                        signButton.isEnabled = false
                        sendTransactionToIroha(
                            irohaIpAddressEditText.text.toString(),
                            signedTx,
                            {
                                signButton.isEnabled = true
                                toast("Transaction has been sent")
                            },
                            {
                                signButton.isEnabled = true
                                toast("Cannot send transaction")
                            })
                    }
                }
            }
        }
    }

    /**
     * Sends a transaction to Iroha node
     * @param irohaIPAddress - Iroha IP address
     * @param transaction - transaction to send
     * @param onSuccess - function that is executed if a given tx is successfully committed
     * @param onFail - function that is executed on error
     */
    private fun sendTransactionToIroha(
        irohaIPAddress: String,
        transaction: TransactionOuterClass.Transaction,
        onSuccess: () -> Unit,
        onFail: () -> Unit
    ) {
        //TODO this thing must be closed properly
        val iroha = IrohaAPI(irohaIPAddress, 50051)
        val irohaConsumer = IrohaConsumer(iroha)
        doAsync {
            irohaConsumer.send(transaction).fold({ uiThread { onSuccess() } },
                { ex ->
                    Log.e(TAG, "Cannot send transaction to Iroha", ex)
                    uiThread { onFail() }
                })
        }
    }

    /**
     * Creates a simple `GrantPermissionTransaction` query
     * @return unsigned `GrantPermissionTransaction` query
     */
    private fun createGrantPermissionTransaction(accountId: String) = TransactionBuilder(userAtDomain, System.currentTimeMillis())
        .grantPermission(accountId, Primitive.GrantablePermission.can_add_my_signatory)
        .build()


    /**
     * Creates a simple `AddSignatory` query
     * @return unsigned `AddSignatory` query
     */
    private fun createAddSignatoryTransaction(accountId: String, publicKey: ByteArray?) = TransactionBuilder(managerAtDomain, System.currentTimeMillis())
        .addSignatory(accountId, publicKey)
        .build()

    /**
     * Creates a simple `RemoveSignatory` transaction
     * @return unsigned `RemoveSignatory` transaction
     */
    private fun createRemoveSignatoryTransaction(publicKey: ByteArray?) = TransactionBuilder(userAtDomain, System.currentTimeMillis())
        .removeSignatory(userAtDomain, publicKey)
        .build()

    /**
     * Creates a simple `SetAccountDetail` transaction
     * @return unsigned `SetAccountDetail` transaction
     */
    private fun createTransaction() = TransactionBuilder(userAtDomain, System.currentTimeMillis())
        .setAccountDetail(userAtDomain, "time", System.currentTimeMillis().toString())
        .build()

    /**
     * Creates a signature object
     * @param signatureBytes - signature bytes
     * @return signature
     */
    private fun formSignature(signatureBytes: ByteArray, publicKey: ByteArray?): Primitive.Signature {
        return Primitive.Signature.newBuilder()
            .setSignature(Utils.toHex(signatureBytes))
            .setPublicKey(Utils.toHex(publicKey))
            .build()
    }
}
