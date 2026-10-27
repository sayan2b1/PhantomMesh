package com.phantom.mesh.util

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

object ContactHelper {

    fun extractContactCard(context: Context, contactUri: Uri): String? {
        val resolver = context.contentResolver
        var contactId: String? = null
        var displayName: String? = null

        // 1. Query contact basic info (ID and Display Name)
        try {
            resolver.query(
                contactUri,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                    val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    if (idIdx != -1) contactId = cursor.getString(idIdx)
                    if (nameIdx != -1) displayName = cursor.getString(nameIdx)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ContactHelper", "Failed to query contact basic info: ${e.message}", e)
        }

        if (displayName.isNullOrBlank()) {
            return null
        }

        // 2. Query phone numbers associated with this contact
        val phoneNumbers = mutableListOf<String>()
        if (!contactId.isNullOrBlank()) {
            try {
                resolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId),
                    null
                )?.use { pCursor ->
                    val numIdx = pCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (pCursor.moveToNext()) {
                        if (numIdx != -1) {
                            val rawNum = pCursor.getString(numIdx)?.trim()
                            if (!rawNum.isNullOrBlank() && !phoneNumbers.contains(rawNum)) {
                                phoneNumbers.add(rawNum)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ContactHelper", "Failed to query contact phone numbers: ${e.message}", e)
            }
        }

        // If phone query by contact ID failed or yielded nothing, check if URI directly has DATA
        if (phoneNumbers.isEmpty()) {
            try {
                resolver.query(
                    contactUri,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    null,
                    null,
                    null
                )?.use { pCursor ->
                    val numIdx = pCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (numIdx != -1 && pCursor.moveToFirst()) {
                        val num = pCursor.getString(numIdx)?.trim()
                        if (!num.isNullOrBlank()) {
                            phoneNumbers.add(num)
                        }
                    }
                }
            } catch (ignored: Exception) {}
        }

        val sb = StringBuilder()
        sb.append("👤 Contact Card: $displayName\n")
        if (phoneNumbers.isNotEmpty()) {
            phoneNumbers.forEach { number ->
                sb.append("📞 Mobile: $number\n")
            }
        } else {
            sb.append("📞 Mobile: No number saved\n")
        }
        return sb.toString().trimEnd()
    }
}
