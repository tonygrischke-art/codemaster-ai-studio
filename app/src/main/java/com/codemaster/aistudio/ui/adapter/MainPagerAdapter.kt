package com.codemaster.aistudio.ui.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.codemaster.aistudio.ui.editor.EditorFragment
import com.codemaster.aistudio.ui.projects.ProjectsFragment
import com.codemaster.aistudio.ui.terminal.TerminalFragment

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    
    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> EditorFragment()
            1 -> TerminalFragment()
            2 -> ProjectsFragment()
            else -> EditorFragment()
        }
    }
}
