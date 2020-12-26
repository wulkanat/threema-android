package ch.threema.app.activities

import android.os.Bundle
import android.os.PersistableBundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import ch.threema.app.R
import com.google.android.material.bottomnavigation.BottomNavigationView

class NavActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)
        setContentView(R.layout.nav_host)

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.nav_bottom_navigation)
        val navController = findNavController(R.id.main_navigation_content)

        bottomNavigationView.setupWithNavController(navController)
        setupActionBarWithNavController(navController)
    }
}
