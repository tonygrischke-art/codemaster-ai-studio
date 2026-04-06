package com.codemaster.aistudio.ui.settings

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.codemaster.aistudio.R

class SettingsDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        
        val editGemini = view.findViewById<EditText>(R.id.edit_gemini_key)
        val editKimi = view.findViewById<EditText>(R.id.edit_kimi_key)
        val btnSave = view.findViewById<Button>(R.id.btn_save)
        
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        editGemini.setText(prefs.getString("gemini_api_key", ""))
        editKimi.setText(prefs.getString("kimi_api_key", ""))
        
        val dialog = AlertDialog.Builder(context, R.style.DarkDialog)
            .setTitle("Settings")
            .setView(view)
            .setNegativeButton("Cancel", null)
            .create()
        
        btnSave.setOnClickListener {
            prefs.edit().apply {
                putString("gemini_api_key", editGemini.text.toString())
                putString("kimi_api_key", editKimi.text.toString())
                apply()
            }
            dialog.dismiss()
        }
        
        return dialog
    }
}
