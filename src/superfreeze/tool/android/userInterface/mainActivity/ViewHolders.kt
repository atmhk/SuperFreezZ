/*
 * Copyright (c) 2019 Hocuri
 *
 * This file is part of SuperFreezZ.
 *
 * SuperFreezZ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SuperFreezZ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SuperFreezZ.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package superfreeze.tool.android.userInterface.mainActivity

/**
 * This file contains the view holders for AppsListAdapter.
 */

import android.app.AlertDialog
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import superfreeze.tool.android.R
import superfreeze.tool.android.backend.FreezerService
import superfreeze.tool.android.backend.getPendingFreezeExplanation
import superfreeze.tool.android.backend.isRootAvailable
import superfreeze.tool.android.database.FreezeMode
import superfreeze.tool.android.database.usageStatsAvailable
import superfreeze.tool.android.isDarkTheme
import java.util.*


abstract class AbstractViewHolder(v: View) : RecyclerView.ViewHolder(v) {
	abstract fun setName(name: String, highlight: String?)
	abstract fun bindTo(item: AbstractListItem)
}


class ViewHolderApp(
	v: View, private val context: Context,
	private val appsListAdapter: AppsListAdapter
) : AbstractViewHolder(v) {

	private val imgIcon: ImageView = v.findViewById(R.id.imgIcon)

	private val txtAppName: TextView = v.findViewById(R.id.txtAppName)
	private val txtExplanation: TextView = v.findViewById(R.id.txtExplanation)
	private val txtNowInactive: TextView = v.findViewById(R.id.txtNowInactive)

	private val modeSymbols: Map<FreezeMode, ImageView> = mapOf(
		FreezeMode.ALWAYS to v.findViewById(R.id.imageAlwaysFreeze),
		FreezeMode.NEVER to v.findViewById(R.id.imageNeverFreeze),
		FreezeMode.WHEN_INACTIVE to v.findViewById(R.id.imageFreezeWhenInactive)
	)

	private lateinit var listItem: ListItemApp

	init {
		v.setOnLongClickListener {
			val infoIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
			infoIntent.addCategory(Intent.CATEGORY_DEFAULT)
			infoIntent.data = Uri.parse("package:${listItem.packageName}")
			context.startActivity(infoIntent)
			true
		}

		v.setOnClickListener {
			// what is done when a list item (that is, an app) is clicked.
			// If the freeze mode is set to NEVER and the freezer (=accessibility) service is enabled, then ask if the user really wants to freeze the app
			if (listItem.freezeMode == FreezeMode.NEVER && FreezerService.isEnabled) {
				AlertDialog.Builder(context)
					.setTitle(listItem.text)
					.setMessage(R.string.freeze_question)
					.setPositiveButton(android.R.string.ok) { _, _ ->
						listItem.freeze(context)
					}
					.setNeutralButton(android.R.string.cancel) { _, _ -> }
					.show()
			} else { // If the freezer service is not enabled, the user will still have the chance to change their mind because when freezing the app manually.
				listItem.freeze(context)
			}
			if (isRootAvailable) {
				appsListAdapter.refresh()
				// This screen does not have to be left to freeze.
				// Usually the apps list is refreshed when onResume() is called, but this will not
				// happen because the MainActivity does not even have to be left.
				// So, refresh() here to clean PENDING_FREEZE and change description if necessary
			}
		}

		if (!usageStatsAvailable) {
			modeSymbols.getValue(FreezeMode.WHEN_INACTIVE).visibility =
				View.GONE
			// Hide as without usagestats we can not know whether an app is 'inactive'
		}

		// Set what happens when one of the mode symbols is clicked
		modeSymbols.forEach { (mode, view) ->
			view.setOnClickListener {
				listItem.setFreezeModeTo(
					mode,
					changeSettings = true,
					showSnackbar = true,
					viewHolder = this
				)
			}
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			modeSymbols.getValue(FreezeMode.ALWAYS).tooltipText = context.getString(
				R.string.always_freeze_this_app
			)
			modeSymbols.getValue(FreezeMode.WHEN_INACTIVE).tooltipText = context.getString(
				R.string.freeze_this_app_if_it_has_not_been_used_for_a_longer_time
			)
			modeSymbols.getValue(FreezeMode.NEVER).tooltipText = context.getString(
				R.string.do_never_freeze_this_app
			)
		}
	}

	private fun setButtonColours(freezeMode: FreezeMode) {
		modeSymbols.forEach { (mode, view) ->
			if (mode == freezeMode) {
				// Show the symbol with the "current" mode in color:
				view.colorFilter = if (isDarkTheme(appsListAdapter.mainActivity)) {
					appsListAdapter.colorFilters[mode] // In dark theme, the colors are different
				} else {
					null
				}
			} else {
				view.colorFilter = appsListAdapter.colorFilterGrey
			}
		}
	}

	private fun refreshExplanation(freezeMode: FreezeMode) {
		txtExplanation.text = getPendingFreezeExplanation(
			freezeMode,
			listItem.applicationInfo,
			appsListAdapter.usageStatsMap?.get(listItem.packageName),
			context
		)
	}

	override fun setName(name: String, highlight: String?) {
		txtAppName.text = name

		if (highlight == null || highlight.isEmpty()) return // nothing to highlight

		val valueLower = name.toLowerCase(Locale.ROOT)
		var offset = 0
		var index = valueLower.indexOf(highlight, offset)
		while (index >= 0 && offset < valueLower.length) {
			val span = SpannableString(txtAppName.text)
			span.setSpan(
				ForegroundColorSpan(Color.BLUE),
				index,
				index + highlight.length,
				Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
			)
			txtAppName.text = span
			offset += index + highlight.length
			index = valueLower.indexOf(highlight, offset)
		}
	}

	override fun bindTo(item: AbstractListItem) {
		listItem = item as ListItemApp
		refresh()
	}

	internal fun refresh() {
		// Refresh name:
		setName(listItem.text, appsListAdapter.searchPattern)

		// Refresh freeze mode:
		setButtonColours(listItem.freezeMode)
		refreshExplanation(listItem.freezeMode)

		// Refresh icon:
		imgIcon.setImageDrawable(appsListAdapter.cacheAppIcon[listItem.packageName])
		if (appsListAdapter.cacheAppIcon[listItem.packageName] == null) {
			GlobalScope.launch {
				listItem.loadNameAndIcon(this@ViewHolderApp)
			}
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
			txtNowInactive.text = if (usm.isAppInactive(listItem.packageName)) {
				" " + context.getString(R.string.currently_inactive_in_brackets)
			} else {
				""
			}
		}
	}
}


internal class ViewHolderSectionHeader(v: View) : AbstractViewHolder(v) {
	private val textView = v.findViewById<TextView>(R.id.textView)

	override fun bindTo(item: AbstractListItem) {
		setName(item.text.toUpperCase(Locale.ROOT), "")
	}

	override fun setName(name: String, highlight: String?) {
		textView.text = name
	}
}