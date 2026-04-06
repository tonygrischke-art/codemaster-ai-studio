package com.codemaster.aistudio.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.codemaster.aistudio.R

class EditorFragment : Fragment() {

    private lateinit var editorText: EditText
    private lateinit var aiStatus: TextView
    private lateinit var btnAiAssist: Button
    private lateinit var btnRun: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_editor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        editorText = view.findViewById(R.id.editor_text)
        aiStatus = view.findViewById(R.id.ai_status)
        btnAiAssist = view.findViewById(R.id.btn_ai_assist)
        btnRun = view.findViewById(R.id.btn_run)

        setupEditor()
        setupAiButton()
    }

    private fun setupEditor() {
        editorText.apply {
            setBackgroundColor(android.graphics.Color.parseColor("#1E1E1E"))
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.GRAY)
        }
    }

    private fun setupAiButton() {
        btnAiAssist.setOnClickListener {
            val code = editorText.text.toString()
            if (code.isNotBlank()) {
                aiStatus.text = "Analyzing code with AI..."
            } else {
                aiStatus.text = "No code to analyze"
            }
        }

        btnRun.setOnClickListener {
            val code = editorText.text.toString()
            if (code.isNotBlank()) {
                aiStatus.text = "Running code..."
            } else {
                aiStatus.text = "No code to run"
            }
        }
    }
}
