package com.jerboa.ui.components.login

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.jerboa.R
import com.jerboa.api.API
import com.jerboa.api.fetchPostsWrapper
import com.jerboa.api.getSiteWrapper
import com.jerboa.api.retrofitErrorHandler
import com.jerboa.datatypes.ListingType
import com.jerboa.datatypes.SortType
import com.jerboa.datatypes.api.Login
import com.jerboa.db.Account
import com.jerboa.db.AccountViewModel
import com.jerboa.fetchInitialData
import com.jerboa.ui.components.home.HomeViewModel
import com.jerboa.ui.components.home.SiteViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {

    var jwt: String by mutableStateOf("")
        private set
    var loading: Boolean by mutableStateOf(false)
        private set
    fun cleanJwt(jwt: String): String {
        return jwt.replace(Regex("[^a-zA-Z0-9-_.]"), "")
    }
    fun login(
        instance: String,
        form: Login,
        navController: NavController,
        accountViewModel: AccountViewModel,
        siteViewModel: SiteViewModel,
        homeViewModel: HomeViewModel,
        ctx: Context,
    ) {
        val originalInstance = API.currentInstance
        val api = API.changeLemmyInstance(instance)

        viewModelScope.launch {
            try {
                loading = true
                try {
                    jwt = cleanJwt(retrofitErrorHandler(api.login(form = form)).jwt!!) // TODO this needs
                    // to be checked,
                } catch (e: java.net.UnknownHostException) {
                    loading = false
                    val msg = ctx.getString(
                        R.string.login_view_model_is_not_a_lemmy_instance,
                        instance,
                    )
                    Log.e("login", e.toString())
                    Toast.makeText(
                        ctx,
                        msg,
                        Toast.LENGTH_SHORT,
                    ).show()
                    API.changeLemmyInstance(originalInstance)
                    this.cancel()
                    return@launch
                }
            } catch (e: Exception) {
                loading = false
                val msg = ctx.getString(R.string.login_view_model_incorrect_login)
                Log.e("login", e.toString())
                Toast.makeText(
                    ctx,
                    msg,
                    Toast.LENGTH_SHORT,
                ).show()
                API.changeLemmyInstance(originalInstance)
                this.cancel()
                return@launch
            }

            // Refetch the site to get your name and id
            // Can't do a co-routine within a co-routine
            siteViewModel.siteRes = getSiteWrapper(auth = jwt, ctx = ctx)

            val luv = siteViewModel.siteRes?.my_user!!.local_user_view
            val account = Account(
                id = luv.person.id,
                name = luv.person.name,
                current = true,
                instance = instance,
                jwt = jwt,
                defaultListingType = listOf("All", "Local", "Subscribed").indexOf(luv.local_user.default_listing_type),
                defaultSortType = listOf(
                    "Active",
                    "Hot",
                    "New",
                    "Old",
                    "TopDay",
                    "TopWeek",
                    "TopMonth",
                    "TopYear",
                    "TopAll",
                    "MostComments",
                    "NewComments").indexOf(luv.local_user.default_sort_type),
            )

            // Refetch the front page
            val posts = fetchPostsWrapper(
                account = account,
                ctx = ctx,
                listingType = ListingType.values()[
                    listOf("All", "Local", "Subscribed").indexOf(luv.local_user
                        .default_listing_type),
                ],
                sortType = SortType.values()[
                    listOf(
                        "Active",
                        "Hot",
                        "New",
                        "Old",
                        "TopDay",
                        "TopWeek",
                        "TopMonth",
                        "TopYear",
                        "TopAll",
                        "MostComments",
                        "NewComments").indexOf(luv.local_user
                            .default_sort_type),
                ],
                page = 1,
            )
            homeViewModel.posts.clear()
            homeViewModel.posts.addAll(posts)

            // Remove the default account
            accountViewModel.removeCurrent()

            // Save that info in the DB
            accountViewModel.insert(account)

            fetchInitialData(
                account = account,
                siteViewModel = siteViewModel,
                homeViewModel = homeViewModel,
            )

            loading = false

            navController.navigate(route = "home")
        }
    }
}
