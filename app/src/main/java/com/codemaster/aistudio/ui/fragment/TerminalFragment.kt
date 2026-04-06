package com.codemaster.aistudio.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.codemaster.aistudio.R
import com.codemaster.aistudio.terminal.EmbeddedTerminalManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class TerminalFragment : Fragment() {

    private lateinit var terminalManager: EmbeddedTerminalManager
    private lateinit var container: FrameLayout
    private lateinit var loadingView: LinearLayout
    private lateinit var progressText: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_terminal, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        container = view.findViewById(R.id.terminal_container)
        loadingView = view.findViewById(R.id.loading_view)
        progressText = view.findViewById(R.id.progress_text)
        
        terminalManager = EmbeddedTerminalManager(requireContext())
        
        lifecycleScope.launch {
            showLoading("Checking terminal environment...")
            terminalManager.preInitialize()
            hideLoading()
            terminalManager.createTerminalView(container)
        }

        setupQuickCommands()
    }

    private fun setupQuickCommands() {
        view?.findViewById<MaterialButton>(R.id.btn_ls)?.setOnClickListener {
            terminalManager.executeCommand("ls -la\n")
        }
        view?.findViewById<MaterialButton>(R.id.btn_git)?.setOnClickListener {
            terminalManager.executeCommand("git status\n")
        }
        view?.findViewById<MaterialButton>(R.id.btn_python)?.setOnClickListener {
            terminalManager.executeCommand("python3\n")
        }
        view?.findViewById<MaterialButton>(R.id.btn_npm)?.setOnClickListener {
            terminalManager.executeCommand("npm --version\n")
        }
        view?.findViewById<MaterialButton>(R.id.btn_clear)?.setOnClickListener {
            terminalManager.executeCommand("clear\n")
        }
    }

    private fun showLoading(message: String) {
        loadingView.visibility = View.VISIBLE
        container.visibility = View.GONE
        progressText.text = message
    }

    private fun hideLoading() {
        loadingView.visibility = View.GONE
        container.visibility = View.VISIBLE
    }
}
