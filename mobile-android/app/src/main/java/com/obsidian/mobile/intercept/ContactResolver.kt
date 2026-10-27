package com.obsidian.mobile.intercept

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log

class ContactResolver(private val context: Context) {

    data class ContactResolution(
        val displayName: String,
        val isSavedContact: Boolean
    )

    fun resolveSender(senderRaw: String): ContactResolution {
        val cleanSender = senderRaw.trim()
        if (cleanSender.isBlank()) {
            return ContactResolution(displayName = "Unknown", isSavedContact = false)
        }

        try {
            // Check if cleanSender is purely numeric / phone number
            val isPhoneNumber = cleanSender.matches(Regex("^[+]?[0-9\\s\\-()]+$"))

            if (isPhoneNumber) {
                val uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(cleanSender)
                )
                val cursor: Cursor? = context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                    null,
                    null,
                    null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            val resolvedName = it.getString(nameIndex)
                            return ContactResolution(displayName = resolvedName, isSavedContact = true)
                        }
                    }
                }
                return ContactResolution(displayName = cleanSender, isSavedContact = false)
            } else {
                // If it's a name (as WhatsApp often displays for saved contacts)
                val cursor = context.contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
                    "${ContactsContract.Contacts.DISPLAY_NAME} = ?",
                    arrayOf(cleanSender),
                    null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        return ContactResolution(displayName = cleanSender, isSavedContact = true)
                    }
                }

                // If not found in contacts table, it might be an unknown sender name or group
                return ContactResolution(displayName = cleanSender, isSavedContact = false)
            }
        } catch (e: SecurityException) {
            Log.w("ContactResolver", "READ_CONTACTS permission not granted: ${e.message}")
            return ContactResolution(displayName = cleanSender, isSavedContact = false)
        } catch (e: Exception) {
            Log.e("ContactResolver", "Error resolving contact: ${e.message}")
            return ContactResolution(displayName = cleanSender, isSavedContact = false)
        }
    }
}
