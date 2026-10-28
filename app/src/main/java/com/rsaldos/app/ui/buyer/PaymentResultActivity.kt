package com.rsaldos.app.ui.buyer

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rsaldos.app.R
import com.rsaldos.app.data.Format
import com.rsaldos.app.data.PaymentResult
import com.rsaldos.app.data.Repository
import com.rsaldos.app.databinding.ActivityPaymentResultBinding
import com.rsaldos.app.ui.common.RoleHomeActivity
import com.rsaldos.app.ui.common.color
import com.rsaldos.app.ui.common.tint

class PaymentResultActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPaymentResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val order = Repository.order(intent.getStringExtra(EXTRA_ORDER).orEmpty())
        val result = PaymentResult.valueOf(intent.getStringExtra(EXTRA_RESULT) ?: PaymentResult.APPROVED.name)

        val (icon, colorRes, bgRes) = when (result) {
            PaymentResult.APPROVED -> Triple(R.drawable.ic_check_circle, R.color.rs_green, R.color.rs_green_soft)
            PaymentResult.PENDING -> Triple(R.drawable.ic_schedule, R.color.rs_yellow, R.color.rs_yellow_soft)
            PaymentResult.REJECTED -> Triple(R.drawable.ic_cancel, R.color.rs_red, R.color.rs_red_soft)
        }
        binding.ivStatus.setImageResource(icon)
        binding.ivStatus.tint(colorRes)
        binding.ivStatus.backgroundTintList = ColorStateList.valueOf(color(bgRes))

        binding.tvStatusTitle.text = getString(
            when (result) {
                PaymentResult.APPROVED -> R.string.result_approved
                PaymentResult.PENDING -> R.string.result_pending
                PaymentResult.REJECTED -> R.string.result_rejected
            }
        )
        binding.tvStatusMessage.text = getString(
            when (result) {
                PaymentResult.APPROVED -> R.string.result_approved_message
                PaymentResult.PENDING -> R.string.result_pending_message
                PaymentResult.REJECTED -> R.string.result_rejected_message
            }
        )
        if (order != null) {
            binding.tvDetails.text = getString(
                R.string.result_details, order.id, order.paymentRef, Format.money(order.total), order.paymentMethod, order.date
            )
        }

        if (result == PaymentResult.REJECTED) {
            binding.btnPrimary.text = getString(R.string.result_retry)
            binding.btnPrimary.setOnClickListener { finish() }
            binding.btnSecondary.text = getString(R.string.result_change_method)
            binding.btnSecondary.setOnClickListener { finish() }
        } else {
            binding.btnPrimary.text = getString(R.string.result_view_orders)
            binding.btnPrimary.setOnClickListener { goHome(R.id.nav_orders) }
            binding.btnSecondary.text = getString(R.string.result_keep_shopping)
            binding.btnSecondary.setOnClickListener { goHome(R.id.nav_home) }
        }
    }

    private fun goHome(tab: Int) {
        val intent = Intent(this, BuyerHomeActivity::class.java)
            .putExtra(RoleHomeActivity.EXTRA_TAB, tab)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    companion object {
        const val EXTRA_ORDER = "order_id"
        const val EXTRA_RESULT = "result"
    }
}
