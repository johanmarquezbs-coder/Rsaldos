package com.rsaldos.app.ui.common

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.rsaldos.app.R
import com.rsaldos.app.data.AuditEvent
import com.rsaldos.app.data.CartItem
import com.rsaldos.app.data.Format
import com.rsaldos.app.data.Order
import com.rsaldos.app.data.Product
import com.rsaldos.app.data.Role
import com.rsaldos.app.data.User
import com.rsaldos.app.databinding.ItemCartBinding
import com.rsaldos.app.databinding.ItemOrderBinding
import com.rsaldos.app.databinding.ItemProductGridBinding
import com.rsaldos.app.databinding.ItemProductRowBinding
import com.rsaldos.app.databinding.ItemSimpleRowBinding
import com.rsaldos.app.databinding.ItemUserBinding

class ProductGridAdapter(
    private var items: List<Product>,
    private val onClick: (Product) -> Unit,
    private val onAdd: (Product) -> Unit
) : RecyclerView.Adapter<ProductGridAdapter.VH>() {
    class VH(val b: ItemProductGridBinding) : RecyclerView.ViewHolder(b.root)

    fun submit(list: List<Product>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemProductGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        val b = holder.b
        b.ivProduct.setImageResource(p.category.icon)
        b.tvTitle.text = p.title
        b.tvGrade.badge(p.grade.label, p.grade.color, p.grade.bg)
        b.tvPrice.text = Format.money(p.price)
        b.tvOldPrice.text = if (p.oldPrice > p.price) Format.money(p.oldPrice) else ""
        b.tvOldPrice.paintFlags = b.tvOldPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        if (p.discountPercent > 0) {
            b.tvDiscount.badge("-${p.discountPercent}%", R.color.rs_white, R.color.rs_orange)
        } else {
            b.tvDiscount.visibility = View.GONE
        }
        b.root.setOnClickListener { onClick(p) }
        b.ivAdd.setOnClickListener { onAdd(p) }
        b.ivFav.setOnClickListener { b.ivFav.tint(R.color.rs_red) }
    }
}

class ProductRowAdapter(
    private var items: List<Product>,
    private val showStatus: Boolean,
    private val actionIcon: Int,
    private val onClick: (Product) -> Unit,
    private val onAction: (Product, View) -> Unit
) : RecyclerView.Adapter<ProductRowAdapter.VH>() {
    class VH(val b: ItemProductRowBinding) : RecyclerView.ViewHolder(b.root)

    fun submit(list: List<Product>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemProductRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        val b = holder.b
        val ctx = b.root.context
        b.ivProduct.setImageResource(p.category.icon)
        b.tvTitle.text = p.title
        b.tvGrade.badge(p.grade.label, p.grade.color, p.grade.bg)
        if (showStatus) {
            b.tvStatus.badge(p.status.label, p.status.color, p.status.bg)
            b.tvMeta.text = ctx.getString(R.string.row_meta_seller, p.condition.label, p.stock, p.maskedSerial)
        } else {
            b.tvStatus.visibility = View.GONE
            b.tvMeta.text = ctx.getString(R.string.row_meta_buyer, p.condition.label, p.warrantyMonths)
        }
        b.tvPrice.text = Format.money(p.price)
        b.ivAction.setImageResource(actionIcon)
        b.ivAction.tint(if (actionIcon == R.drawable.ic_add) R.color.rs_orange else R.color.rs_graphite)
        b.root.setOnClickListener { onClick(p) }
        b.ivAction.setOnClickListener { onAction(p, it) }
    }
}

class CartAdapter(
    private var items: List<CartItem>,
    private val onQuantity: (CartItem, Int) -> Unit,
    private val onDelete: (CartItem) -> Unit,
    private val onClick: (CartItem) -> Unit
) : RecyclerView.Adapter<CartAdapter.VH>() {
    class VH(val b: ItemCartBinding) : RecyclerView.ViewHolder(b.root)

    fun submit(list: List<CartItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val b = holder.b
        b.ivProduct.setImageResource(item.product.category.icon)
        b.tvTitle.text = item.product.title
        b.tvGrade.badge(item.product.grade.label, item.product.grade.color, item.product.grade.bg)
        b.tvPrice.text = Format.money(item.subtotal)
        b.tvQty.text = item.quantity.toString()
        b.btnMinus.setOnClickListener { onQuantity(item, item.quantity - 1) }
        b.btnPlus.setOnClickListener { onQuantity(item, item.quantity + 1) }
        b.ivDelete.setOnClickListener { onDelete(item) }
        b.root.setOnClickListener { onClick(item) }
    }
}

class OrderAdapter(
    private var items: List<Order>,
    private val actionLabel: (Order) -> String,
    private val sellerId: Int? = null,
    private val onClick: (Order) -> Unit
) : RecyclerView.Adapter<OrderAdapter.VH>() {
    class VH(val b: ItemOrderBinding) : RecyclerView.ViewHolder(b.root)

    fun submit(list: List<Order>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val o = items[position]
        val b = holder.b
        val ctx = b.root.context
        val lines = if (sellerId != null) o.lines.filter { it.sellerId == sellerId } else o.lines
        b.tvOrderId.text = ctx.getString(R.string.order_number, o.id)
        b.tvStatus.badge(o.status.label, o.status.color, o.status.bg)
        b.tvDate.text = ctx.getString(R.string.order_date_method, o.date, o.paymentMethod)
        b.tvItems.text = lines.joinToString("\n") { "${it.quantity} × ${it.title}" }
        b.tvTotal.text = Format.money(if (sellerId != null) lines.sumOf { it.subtotal } else o.total)
        b.tvAction.text = actionLabel(o)
        b.root.setOnClickListener { onClick(o) }
    }
}

class UserAdapter(
    private var items: List<User>,
    private val onClick: (User) -> Unit,
    private val onMore: (User, View) -> Unit
) : RecyclerView.Adapter<UserAdapter.VH>() {
    class VH(val b: ItemUserBinding) : RecyclerView.ViewHolder(b.root)

    fun submit(list: List<User>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val u = items[position]
        val b = holder.b
        b.tvAvatar.avatar(u.initials, avatarColor(u.id))
        b.tvName.text = u.name
        b.tvEmail.text = u.email
        val roleColors = when (u.role) {
            Role.BUYER -> R.color.rs_graphite_soft to R.color.rs_gray_light
            Role.SELLER -> R.color.rs_blue to R.color.rs_blue_soft
            Role.ADMIN -> R.color.rs_orange_dark to R.color.rs_orange_soft
        }
        b.tvRole.badge(u.role.label, roleColors.first, roleColors.second)
        b.tvStatus.badge(u.status.label, u.status.color, u.status.bg)
        b.root.setOnClickListener { onClick(u) }
        b.ivMore.setOnClickListener { onMore(u, it) }
    }
}

class AuditAdapter(private var items: List<AuditEvent>) : RecyclerView.Adapter<AuditAdapter.VH>() {
    class VH(val b: ItemSimpleRowBinding) : RecyclerView.ViewHolder(b.root)

    fun submit(list: List<AuditEvent>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemSimpleRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val pad = parent.resources.getDimensionPixelSize(R.dimen.space_l)
        b.root.setPadding(pad, b.root.paddingTop, pad, b.root.paddingBottom)
        return VH(b)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]
        val b = holder.b
        b.ivRowIcon.setImageResource(e.type.icon)
        b.ivRowIcon.tint(e.type.color)
        b.tvRowTitle.text = e.action
        b.tvRowSubtitle.text = b.root.context.getString(R.string.audit_row, e.actor, e.detail)
        b.tvRowTrailing.text = e.date.replace(" ", "\n")
    }
}
