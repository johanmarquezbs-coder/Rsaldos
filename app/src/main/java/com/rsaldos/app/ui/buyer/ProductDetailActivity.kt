package com.rsaldos.app.ui.buyer

import android.graphics.Paint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rsaldos.app.R
import com.rsaldos.app.data.Format
import com.rsaldos.app.data.Product
import com.rsaldos.app.data.Repository
import com.rsaldos.app.data.SessionManager
import com.rsaldos.app.data.Role
import com.rsaldos.app.databinding.ActivityProductDetailBinding
import com.rsaldos.app.ui.common.avatar
import com.rsaldos.app.ui.common.badge
import com.rsaldos.app.ui.common.visible
import com.rsaldos.app.ui.common.toast

class ProductDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProductDetailBinding
    private lateinit var product: Product
    private var quantity = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val found = Repository.product(intent.getIntExtra(EXTRA_ID, -1))
        if (found == null) {
            toast(getString(R.string.product_not_found))
            finish()
            return
        }
        product = found

        binding.appBar.root.title = getString(R.string.detail_title)
        binding.appBar.root.setNavigationOnClickListener { finish() }
        bind()

        binding.btnMinus.setOnClickListener { setQuantity(quantity - 1) }
        binding.btnPlus.setOnClickListener { setQuantity(quantity + 1) }
        binding.btnAddCart.setOnClickListener { addToCart() }

        val role = SessionManager(this).currentUser()?.role
        if (role != Role.BUYER) {
            binding.btnAddCart.isEnabled = false
            binding.btnAddCart.text = getString(R.string.detail_preview_only)
        }
    }

    private fun bind() {
        val p = product
        binding.ivProduct.setImageResource(p.category.icon)
        if (p.discountPercent > 0) binding.tvDiscount.badge("-${p.discountPercent}%", R.color.rs_white, R.color.rs_orange)
        else binding.tvDiscount.visible(false)
        binding.tvPhotos.badge(getString(R.string.detail_photos, p.photos), R.color.rs_white, R.color.rs_graphite)
        binding.tvGrade.badge(p.grade.label, p.grade.color, p.grade.bg)
        binding.tvCondition.badge(p.condition.label, p.condition.color, p.condition.bg)
        if (p.stock == 1) binding.tvStock.badge(getString(R.string.last_unit), R.color.rs_red, R.color.rs_red_soft)
        else binding.tvStock.badge(getString(R.string.units_available, p.stock), R.color.rs_graphite_soft, R.color.rs_gray_light)

        binding.tvTitle.text = p.title
        binding.tvBrand.text = getString(R.string.detail_brand, p.brand, p.category.label)
        binding.tvPrice.text = Format.money(p.price)
        if (p.oldPrice > p.price) {
            binding.tvOldPrice.text = Format.money(p.oldPrice)
            binding.tvOldPrice.paintFlags = binding.tvOldPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        }
        binding.tvTests.text = p.tests
        binding.tvWarranty.text = getString(R.string.detail_warranty, p.warrantyMonths)
        binding.tvDefects.text = getString(R.string.detail_defects, p.defects)
        binding.tvSerial.text = getString(R.string.detail_serial, p.maskedSerial, p.grade.detail)
        binding.tvDescription.text = p.description

        val seller = Repository.findUser(p.sellerId)
        val sellerName = seller?.let { s -> s.storeName.ifBlank { s.name } } ?: getString(R.string.app_name)
        binding.tvSellerAvatar.avatar(seller?.initials ?: "RS", R.color.rs_graphite)
        binding.tvSellerName.text = getString(R.string.detail_sold_by, sellerName)
        binding.tvSellerMeta.text = getString(R.string.detail_seller_meta, p.rating.toString().replace('.', ','))
        setQuantity(1)
    }

    private fun setQuantity(value: Int) {
        if (value < 1 || value > product.stock) return
        quantity = value
        binding.tvQty.text = quantity.toString()
    }

    private fun addToCart() {
        if (Repository.addToCart(product, quantity)) {
            toast(getString(R.string.cart_added, product.title))
            finish()
        } else {
            toast(getString(R.string.cart_no_stock))
        }
    }

    companion object {
        const val EXTRA_ID = "product_id"
    }
}
