package com.codemaster.aistudio.ui.editor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.codemaster.aistudio.R
import com.google.android.material.floatingactionbutton.FloatingActionButton

class EditorFragment : Fragment() {

    private lateinit var codeEditor: EditText
    private lateinit var fabRun: FloatingActionButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_editor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        codeEditor = view.findViewById(R.id.code_editor)
        fabRun = view.findViewById(R.id.fab_run)
        
        fabRun.setOnClickListener {
            val code = codeEditor.text.toString()
        }
    }

    fun getCode(): String = codeEditor.text.toString()
    
    fun setCode(code: String) {
        codeEditor.setText(code)
    }
}
