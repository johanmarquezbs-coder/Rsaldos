package com.rsaldos.app.ui.buyer

import android.Manifest
import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.rsaldos.app.R
import com.rsaldos.app.data.Format
import com.rsaldos.app.data.PaymentResult
import com.rsaldos.app.data.Repository
import com.rsaldos.app.data.SessionManager
import com.rsaldos.app.data.User
import com.rsaldos.app.databinding.ActivityCheckoutBinding
import com.rsaldos.app.ui.common.color
import com.rsaldos.app.ui.common.visible
import com.rsaldos.app.ui.common.toast

@Suppress("DEPRECATION")
class CheckoutActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCheckoutBinding
    private lateinit var user: User
    private val handler = Handler(Looper.getMainLooper())

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            user.locationConsent = true
            binding.tvLocationStatus.text = getString(R.string.checkout_location_ok, user.city)
            binding.tvLocationStatus.setTextColor(color(R.color.rs_green))
        } else {
            binding.tvLocationStatus.text = getString(R.string.checkout_location_denied)
            binding.tvLocationStatus.setTextColor(color(R.color.rs_red))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheckoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val current = SessionManager(this).currentUser()
        if (current == null || Repository.cart.isEmpty()) {
            toast(getString(R.string.cart_empty_title))
            finish()
            return
        }
        user = current

        binding.appBar.root.title = getString(R.string.checkout_title)
        binding.appBar.root.setNavigationOnClickListener { finish() }

        binding.etAddress.setText(user.address)
        binding.etCity.setText(user.city)
        binding.btnUseLocation.setOnClickListener {
            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        binding.rgDelivery.setOnCheckedChangeListener { _, _ -> updateSummary() }
        binding.rgPayment.setOnCheckedChangeListener { _, checkedId -> onPaymentMethod(checkedId) }
        binding.btnPay.setOnClickListener { pay() }
        updateSummary()
    }

    private fun shipping(): Long = if (binding.rbPickup.isChecked) 0L else Repository.SHIPPING_COST

    private fun total(): Long = Repository.cartTotal() + shipping()

    private fun updateSummary() {
        binding.tvItems.text = Repository.cart.joinToString("\n") {
            "${it.quantity} × ${it.product.title} (${it.product.grade.label}) — ${Format.money(it.subtotal)}"
        }
        binding.tvSummary.text = getString(
            R.string.checkout_summary,
            Format.money(Repository.cartTotal()),
            if (shipping() == 0L) getString(R.string.free) else Format.money(shipping()),
            Format.money(total())
        )
        binding.btnPay.text = getString(R.string.checkout_pay, Format.money(total()))
    }

    private fun onPaymentMethod(checkedId: Int) {
        val isCard = checkedId == R.id.rbCard
        binding.layoutCard.visible(isCard)
        binding.tvAltPayment.visible(!isCard)
        binding.tvAltPayment.text = when (checkedId) {
            R.id.rbPse -> getString(R.string.checkout_pse_info)
            R.id.rbNequi -> getString(R.string.checkout_nequi_info)
            else -> ""
        }
    }

    private fun pay() {
        listOf(binding.tilAddress, binding.tilCity, binding.tilCardNumber, binding.tilExpiry, binding.tilCvv, binding.tilCardName)
            .forEach { it.error = null }
        val address = binding.etAddress.text?.toString()?.trim().orEmpty()
        val city = binding.etCity.text?.toString()?.trim().orEmpty()
        if (address.length < 6) { binding.tilAddress.error = getString(R.string.error_address); return }
        if (city.isEmpty()) { binding.tilCity.error = getString(R.string.error_required); return }

        val method: String
        val result: PaymentResult
        when (binding.rgPayment.checkedRadioButtonId) {
            R.id.rbCard -> {
                val number = binding.etCardNumber.text?.toString()?.filter { it.isDigit() }.orEmpty()
                val expiry = binding.etExpiry.text?.toString().orEmpty()
                val cvv = binding.etCvv.text?.toString().orEmpty()
                val name = binding.etCardName.text?.toString()?.trim().orEmpty()
                if (number.length !in 13..19 || !luhn(number)) { binding.tilCardNumber.error = getString(R.string.error_card); return }
                if (!Regex("^(0[1-9]|1[0-2])/\\d{2}$").matches(expiry)) { binding.tilExpiry.error = getString(R.string.error_expiry); return }
                if (cvv.length !in 3..4) { binding.tilCvv.error = getString(R.string.error_cvv); return }
                if (name.length < 3) { binding.tilCardName.error = getString(R.string.error_required); return }
                method = getString(R.string.pay_card_masked, number.takeLast(4))
                result = if (number.endsWith("0002")) PaymentResult.REJECTED else PaymentResult.APPROVED
            }
            R.id.rbPse -> { method = getString(R.string.pay_pse); result = PaymentResult.PENDING }
            else -> { method = getString(R.string.pay_nequi); result = PaymentResult.APPROVED }
        }
        if (!binding.cbTerms.isChecked) {
            toast(getString(R.string.error_terms))
            return
        }

        binding.btnPay.isEnabled = false
        val progress = ProgressDialog(this).apply {
            setMessage(getString(R.string.checkout_processing))
            setCancelable(false)
            show()
        }
        handler.postDelayed({
            progress.dismiss()
            val fullAddress = "$address, $city"
            val order = Repository.checkout(user, fullAddress, method, result)
            startActivity(Intent(this, PaymentResultActivity::class.java)
                .putExtra(PaymentResultActivity.EXTRA_ORDER, order.id)
                .putExtra(PaymentResultActivity.EXTRA_RESULT, result.name))
            binding.btnPay.isEnabled = true
            if (result != PaymentResult.REJECTED) finish()
        }, 1500)
    }

    private fun luhn(number: String): Boolean {
        var sum = 0
        var alternate = false
        for (i in number.length - 1 downTo 0) {
            var n = number[i] - '0'
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return sum % 10 == 0
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
