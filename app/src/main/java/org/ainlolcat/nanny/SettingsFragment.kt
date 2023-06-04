package org.ainlolcat.nanny

import android.graphics.Color
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.findNavController
import org.ainlolcat.nanny.databinding.FragmentSettingsBinding
import org.ainlolcat.nanny.settings.NannySettings
import com.google.android.material.snackbar.Snackbar
import java.lang.IllegalArgumentException
import java.util.stream.Collectors

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val preferences = context!!.getSharedPreferences("org.ainlolcat.nanny.app",
            AppCompatActivity.MODE_PRIVATE
        )
        val nannySettings = NannySettings(preferences)
        Log.i("SettingsFragment", "Current settings: $nannySettings")

        if (nannySettings.botToken != null) {
            binding.settingBotSecret.text = SpannableStringBuilder(nannySettings.botToken)
        }
        binding.settingAllowedUsers.text = SpannableStringBuilder(nannySettings.allowedUsers.stream().collect(Collectors.joining(";")))

        binding.settingSoundLevelThreshold.text = SpannableStringBuilder(nannySettings.soundLevelThreshold.toString())
        binding.settingAlarmCooldownSec.text = SpannableStringBuilder(nannySettings.alarmCooldownSec.toString())
        binding.settingAlarmCooldownOverrideIncrease.text = SpannableStringBuilder(nannySettings.alarmCooldownOverrideIncreaseThreshold.toString())

        binding.settingBackgroundColor.text = SpannableStringBuilder(nannySettings.backgroundColor)
        binding.settingScreenBrightness.text = SpannableStringBuilder(nannySettings.screenBrightness.toString())

        binding.buttonSaveSettings.setOnClickListener {
            val newBotToken = binding.settingBotSecret.text.toString()
            val newAllowedUsers = ArrayList(binding.settingAllowedUsers.text.toString().split(";"))
            val newKnownChats = nannySettings.knownChats.entries.stream().filter { e -> newAllowedUsers.contains(e.key) }.collect(Collectors.toMap({ x -> x.key},{ x -> x.value}))

            val newThreshold = tryToGetNumber(binding.settingSoundLevelThreshold, nannySettings.soundLevelThreshold, "Sound level threshold", view)
            val newCooldownSec = tryToGetNumber(binding.settingAlarmCooldownSec, nannySettings.alarmCooldownSec, "Cooldown seconds", view)
            val newCooldownOverride = tryToGetNumber(binding.settingAlarmCooldownOverrideIncrease, nannySettings.alarmCooldownOverrideIncreaseThreshold, "Cooldown override delta", view)
            val backgroundColor = tryToGetColor(binding.settingBackgroundColor, nannySettings.backgroundColor?:"white", "Background Color", view)
            val screenBrightness = tryToGetNumber(binding.settingScreenBrightness, nannySettings.alarmCooldownSec, "Screen brightness", view)

            val newNannySettings = NannySettings(
                newBotToken, newAllowedUsers, newKnownChats,
                newThreshold, newCooldownSec, newCooldownOverride,
                backgroundColor, screenBrightness
            )

            newNannySettings.storeSettings(preferences)
            Log.i("SettingsFragment", "New settings: $newNannySettings")

            if (context is MainActivity) {
                (context as MainActivity).initAndApplySettings()
            }

            findNavController().navigate(R.id.action_SettingsFragment_to_InitialFragment)
        }

        binding.buttonDiscardSettings.setOnClickListener {
            findNavController().navigate(R.id.action_SettingsFragment_to_InitialFragment)
        }

        if (context is MainActivity) {
            val colorName = (context as MainActivity).setting.backgroundColor
            view.setBackgroundColor(Color.parseColor(colorName))
        }
    }

    private fun tryToGetColor(settingBackgroundColor: EditText, default: String, nameInMessage: String, view: View): String {
        val backgroundColor = settingBackgroundColor.text.toString()
        try {
            Color.parseColor(backgroundColor)
            return backgroundColor
        } catch (e: IllegalArgumentException) {
            Snackbar.make(view, "$nameInMessage has wrong format. Allowed: #RRGGBB or red, blue, green, black, white, gray, cyan, magenta, yellow, lightgray, darkgray, grey, lightgrey, darkgrey, aqua, fuchsia, lime, maroon, navy, olive, purple, silver, and teal.", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
            return default
        }
    }

    private fun tryToGetNumber(editText: EditText, defValue: Int, nameInMessage: String, view: View): Int {
        val newValueText = editText.text.toString()
        if (newValueText.matches("^[0-9]+$".toRegex())) {
            return Integer.parseInt(newValueText)
        } else {
            Snackbar.make(view, "$nameInMessage should be a number", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
            reenterTransition
            return defValue;
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}