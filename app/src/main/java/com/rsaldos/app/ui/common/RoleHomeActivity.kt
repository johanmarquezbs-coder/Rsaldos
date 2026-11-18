package com.rsaldos.app.ui.common

import android.os.Bundle
import android.view.Menu
import androidx.activity.OnBackPressedCallback
import androidx.annotation.IdRes
import androidx.annotation.MenuRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rsaldos.app.R
import com.rsaldos.app.data.AuditType
import com.rsaldos.app.data.Repository
import com.rsaldos.app.data.SessionManager
import com.rsaldos.app.data.User
import com.rsaldos.app.data.UserStatus
import com.rsaldos.app.databinding.ActivityRoleHomeBinding
import com.rsaldos.app.databinding.NavHeaderBinding

/**
 * Pantalla base de cada rol (Comprador, Vendedor y Administrador).
 *
 * Arma el menú lateral (DrawerLayout) y la barra inferior (BottomNavigationView) del rol,
 * cambia de fragmento según la opción elegida y escucha los cambios del [Repository] para
 * refrescar el fragmento visible cuando llegan datos nuevos desde Firestore. Si el
 * administrador bloquea o elimina la cuenta mientras está abierta, cierra la sesión.
 */
abstract class RoleHomeActivity : AppCompatActivity() {
    protected lateinit var binding: ActivityRoleHomeBinding
    lateinit var session: SessionManager
    private lateinit var initialUser: User

    /** Perfil vigente del usuario (se actualiza en tiempo real desde Firestore). */
    val user: User get() = Repository.me ?: initialUser

    /** Observador que refresca el fragmento actual y la insignia del carrito. */
    private val dataObserver: () -> Unit = {
        if (::binding.isInitialized) {
            (supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? Refreshable)?.let { fragment ->
                if ((fragment as Fragment).isResumed) fragment.refresh()
            }
            updateCartBadge()
            bindHeader()
        }
    }

    @get:MenuRes protected abstract val bottomMenu: Int
    @get:MenuRes protected abstract val drawerMenu: Int
    @get:IdRes protected abstract val startDestination: Int

    /** Devuelve el fragmento que corresponde a una opción del menú, o null si no aplica. */
    protected abstract fun fragmentFor(@IdRes itemId: Int): Fragment?

    /** Permite a cada rol atender opciones del menú lateral que abren otra actividad. */
    protected open fun onDrawerAction(@IdRes itemId: Int): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        val current = session.currentUser()
        if (current == null) {
            Navigator.restart(this)
            finish()
            return
        }
        initialUser = current

        binding = ActivityRoleHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNav.inflateMenu(bottomMenu)
        binding.navView.inflateMenu(drawerMenu)
        bindHeader()

        binding.toolbar.setNavigationOnClickListener { binding.drawerLayout.openDrawer(GravityCompat.START) }

        if (savedInstanceState == null) {
            val first = intent.getIntExtra(EXTRA_TAB, startDestination)
            binding.bottomNav.selectedItemId = first
            show(first)
        } else {
            binding.toolbar.title = binding.bottomNav.menu.findItem(binding.bottomNav.selectedItemId)?.title
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            show(item.itemId)
            true
        }
        binding.bottomNav.setOnItemReselectedListener { /* sin acción: evita recargar */ }

        binding.navView.setNavigationItemSelectedListener { item ->
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            when (item.itemId) {
                R.id.nav_logout -> confirmLogout()
                R.id.nav_help -> showHelp()
                else -> if (!onDrawerAction(item.itemId)) navigateTo(item.itemId)
            }
            true
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.drawerLayout.isDrawerOpen(GravityCompat.START) ->
                        binding.drawerLayout.closeDrawer(GravityCompat.START)
                    binding.bottomNav.selectedItemId != startDestination ->
                        binding.bottomNav.selectedItemId = startDestination
                    else -> finish()
                }
            }
        })
    }

    override fun onStart() {
        super.onStart()
        if (!::binding.isInitialized) return
        Repository.addObserver(dataObserver)
        Repository.onSessionRevoked = { status -> onRevoked(status) }
    }

    override fun onStop() {
        Repository.removeObserver(dataObserver)
        Repository.onSessionRevoked = null
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) updateCartBadge()
    }

    /** Cierra la sesión cuando el administrador bloquea o elimina la cuenta. */
    private fun onRevoked(status: UserStatus) {
        toast(getString(if (status == UserStatus.BLOCKED) R.string.error_blocked else R.string.session_revoked))
        session.logout()
        Navigator.openLogin(this)
        finish()
    }

    /** Muestra nombre, correo y rol en la cabecera del menú lateral. */
    private fun bindHeader() {
        val header = NavHeaderBinding.bind(binding.navView.getHeaderView(0))
        header.tvHeaderAvatar.text = user.initials
        header.tvHeaderAvatar.setTextColor(color(R.color.rs_orange))
        header.tvHeaderName.text = user.name
        header.tvHeaderEmail.text = user.email
        header.tvHeaderRole.badge(user.role.label, R.color.rs_orange_dark, R.color.rs_white)
    }

    /** Navega a una opción del menú (de la barra inferior o del menú lateral). */
    fun navigateTo(@IdRes itemId: Int) {
        if (binding.bottomNav.menu.findItem(itemId) != null) {
            binding.bottomNav.selectedItemId = itemId
        } else {
            show(itemId)
        }
    }

    /** Reemplaza el fragmento visible y actualiza el título de la barra superior. */
    private fun show(@IdRes itemId: Int) {
        val fragment = fragmentFor(itemId) ?: return
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, fragment)
            .commit()
        binding.toolbar.title = findTitle(binding.navView.menu, itemId)
            ?: findTitle(binding.bottomNav.menu, itemId)
        binding.navView.setCheckedItem(itemId)
    }

    /** Título de una opción del menú. */
    private fun findTitle(menu: Menu, itemId: Int): CharSequence? = menu.findItem(itemId)?.title

    /** Muestra en la barra inferior cuántas unidades hay en el carrito. */
    fun updateCartBadge() {
        if (binding.bottomNav.menu.findItem(R.id.nav_cart) == null) return
        val count = Repository.cartCount()
        val badge = binding.bottomNav.getOrCreateBadge(R.id.nav_cart)
        badge.backgroundColor = color(R.color.rs_orange)
        badge.badgeTextColor = color(R.color.rs_white)
        badge.setVisible(count > 0)
        badge.number = count
    }

    /** Pide confirmación, registra el cierre en la auditoría y cierra la sesión de Firebase. */
    fun confirmLogout() {
        confirm(getString(R.string.logout_title), getString(R.string.logout_message), getString(R.string.nav_logout)) {
            Repository.log(user.name, "Cierre de sesión", "Rol ${user.role.label}", AuditType.SECURITY)
            session.logout()
            Navigator.openLogin(this)
            finish()
        }
    }

    /** Diálogo de ayuda general. */
    private fun showHelp() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.help_title)
            .setMessage(R.string.help_message)
            .setPositiveButton(R.string.action_understood, null)
            .show()
    }

    companion object {
        const val EXTRA_TAB = "start_tab"
    }
}
