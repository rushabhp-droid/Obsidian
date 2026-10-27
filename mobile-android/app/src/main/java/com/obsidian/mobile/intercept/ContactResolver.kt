package com.obsidian.mobile.intercept

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/**
 * Is a message's sender a saved contact? Saved contacts' message text never
 * leaves the phone, only their links do. Fails closed: without READ_CONTACTS,
 * or when the lookup fails, every sender counts as saved, so no text is sent.
 */
class ContactResolver(private val context: Context) {

    data class Resolution(val displayName: String, val isSavedContact: Boolean)

    private val phoneLike = Regex("^[+]?[0-9\\s\\-()]{5,}$")

    fun canReadContacts(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun resolve(rawSender: String): Resolution {
        val sender = rawSender.trim().ifEmpty { "Unknown" }
        if (!canReadContacts()) return Resolution(sender, isSavedContact = true)
        return try {
            if (phoneLike.matches(sender)) {
                val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(sender))
                context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) return Resolution(cursor.getString(0) ?: sender, true)
                    }
                Resolution(sender, false)
            } else {
                // WhatsApp shows saved contacts by their display name.
                context.contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
                    "${ContactsContract.Contacts.DISPLAY_NAME} = ?",
                    arrayOf(sender),
                    null,
                )?.use { cursor -> if (cursor.moveToFirst()) return Resolution(sender, true) }
                Resolution(sender, false)
            }
        } catch (e: RuntimeException) {
            Resolution(sender, isSavedContact = true)
        }
    }
}
