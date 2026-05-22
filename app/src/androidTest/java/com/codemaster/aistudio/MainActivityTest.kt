package com.codemaster.aistudio

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityTest {

 @get:Rule
 val hiltRule = HiltAndroidRule(this)

 @Before
 fun init() {
 hiltRule.inject()
 }

 @Test
 fun activityLaunches_withoutCrash() {
 ActivityScenario.launch(MainActivity::class.java).use { scenario ->
 scenario.onActivity { activity ->
 assert(!activity.isFinishing) { "MainActivity finished immediately — likely a crash" }
 assert(!activity.isDestroyed) { "MainActivity was destroyed on launch" }
 }
 }
 }

 @Test
 fun viewPager_isDisplayed() {
 ActivityScenario.launch(MainActivity::class.java).use {
 onView(withId(R.id.viewPager)).check(matches(isDisplayed()))
 }
 }

 @Test
 fun tabLayout_isDisplayed() {
 ActivityScenario.launch(MainActivity::class.java).use {
 onView(withId(R.id.tabLayout)).check(matches(isDisplayed()))
 }
 }

 @Test
 fun fabMenu_isDisplayed() {
 ActivityScenario.launch(MainActivity::class.java).use {
 onView(withId(R.id.fabMenu)).check(matches(isDisplayed()))
 }
 }
}