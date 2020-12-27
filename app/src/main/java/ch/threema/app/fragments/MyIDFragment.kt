/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2020 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package ch.threema.app.fragments

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import androidx.appcompat.widget.AppCompatSpinner
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.DialogFragment
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.activities.ExportIDActivity
import ch.threema.app.activities.ProfilePicRecipientsActivity
import ch.threema.app.activities.ThreemaActivity
import ch.threema.app.asynctasks.DeleteIdentityAsyncTask
import ch.threema.app.asynctasks.LinkWithEmailAsyncTask
import ch.threema.app.dialogs.*
import ch.threema.app.dialogs.GenericAlertDialog.DialogClickListener
import ch.threema.app.dialogs.PasswordEntryDialog.PasswordEntryDialogClickListener
import ch.threema.app.dialogs.TextEntryDialog.TextEntryDialogClickListener
import ch.threema.app.emojis.EmojiTextView
import ch.threema.app.listeners.ProfileListener
import ch.threema.app.listeners.SMSVerificationListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.routines.CheckIdentityRoutine
import ch.threema.app.services.*
import ch.threema.app.ui.AvatarEditView
import ch.threema.app.ui.ImagePopup
import ch.threema.app.ui.QRCodePopup
import ch.threema.app.utils.*
import ch.threema.base.ThreemaException
import ch.threema.client.LinkMobileNoException
import ch.threema.client.ProtocolDefines
import ch.threema.localcrypto.MasterKeyLockedException
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

open class MyIDFragment : MainFragment(), View.OnClickListener, DialogClickListener, TextEntryDialogClickListener, PasswordEntryDialogClickListener {
    private var serviceManager: ServiceManager? = null
    private var userService: UserService? = null
    private var preferenceService: PreferenceService? = null
    private var fingerPrintService: FingerPrintService? = null
    private var localeService: LocaleService? = null
    private var contactService: ContactService? = null
    private var fileService: FileService? = null
    private var avatarView: AvatarEditView? = null
    private var nicknameTextView: EmojiTextView? = null
    private var hidden = false
    private var isReadonlyProfile = false
    private var isDisabledProfilePicReleaseSettings = false
    private val smsVerificationListener: SMSVerificationListener = object : SMSVerificationListener {
        override fun onVerified() {
            RuntimeUtil.runOnUiThread { updatePendingState(view, false) }
        }

        override fun onVerificationStarted() {
            RuntimeUtil.runOnUiThread { updatePendingState(view, false) }
        }
    }
    private val profileListener: ProfileListener = object : ProfileListener {
        override fun onAvatarChanged() {}
        override fun onAvatarRemoved() {}
        override fun onNicknameChanged(newNickname: String) {
            reloadNickname()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (!requiredInstances()) {
            logger.error("could not instantiate required objects")
            return null
        }
        val fragmentView = view ?: inflater.inflate(R.layout.fragment_my_id, container, false)

        updatePendingState(fragmentView, true)
        val layoutTransition = LayoutTransition()
        layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
        val viewGroup = fragmentView!!.findViewById<ViewGroup>(R.id.fragment_id_container)
        viewGroup.layoutTransition = layoutTransition
        if (ConfigUtils.isWorkRestricted()) {
            AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__readonly_profile))?.let {
                isReadonlyProfile = it
            }
            AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__disable_send_profile_picture))?.let {
                isDisabledProfilePicReleaseSettings = it
            }
        }
        val textView = fragmentView.findViewById<TextView>(R.id.keyfingerprint)
        textView.text = fingerPrintService!!.getFingerPrint(identity)
        fragmentView.findViewById<View>(R.id.policy_explain).visibility = if (isReadonlyProfile || AppRestrictionUtil.isBackupsDisabled(ThreemaApplication.getAppContext()) || AppRestrictionUtil.isIdBackupsDisabled(ThreemaApplication.getAppContext())) View.VISIBLE else View.GONE
        val picReleaseConfImageView = fragmentView.findViewById<ImageView>(R.id.picrelease_config)
        picReleaseConfImageView.setOnClickListener(this)
        picReleaseConfImageView.visibility = if (preferenceService!!.profilePicRelease == PreferenceService.PROFILEPIC_RELEASE_SOME) View.VISIBLE else View.GONE
        configureEditWithButton(fragmentView.findViewById(R.id.linked_email_layout), fragmentView.findViewById(R.id.change_email), isReadonlyProfile)
        configureEditWithButton(fragmentView.findViewById(R.id.linked_mobile_layout), fragmentView.findViewById(R.id.change_mobile), isReadonlyProfile)
        configureEditWithButton(fragmentView.findViewById(R.id.delete_id_layout), fragmentView.findViewById(R.id.delete_id), isReadonlyProfile)
        configureEditWithButton(fragmentView.findViewById(R.id.revocation_key_layout), fragmentView.findViewById(R.id.revocation_key), isReadonlyProfile)
        configureEditWithButton(fragmentView.findViewById(R.id.export_id_layout), fragmentView.findViewById(R.id.export_id), AppRestrictionUtil.isBackupsDisabled(ThreemaApplication.getAppContext()) ||
                AppRestrictionUtil.isIdBackupsDisabled(ThreemaApplication.getAppContext()))
        if (userService != null && userService!!.identity != null) {
            (fragmentView.findViewById<View>(R.id.my_id) as TextView).text = userService!!.identity
            fragmentView.findViewById<View>(R.id.my_id_share).setOnClickListener(this)
            fragmentView.findViewById<View>(R.id.my_id_qr).setOnClickListener(this)
        }
        avatarView = fragmentView.findViewById(R.id.avatar_edit_view)
        avatarView!!.setFragment(this)
        avatarView!!.setContactModel(contactService!!.me)
        nicknameTextView = fragmentView.findViewById(R.id.nickname)
        if (isReadonlyProfile) {
            fragmentView.findViewById<View>(R.id.profile_edit).visibility = View.GONE
        } else {
            fragmentView.findViewById<View>(R.id.profile_edit).visibility = View.VISIBLE
            fragmentView.findViewById<View>(R.id.profile_edit).setOnClickListener(this)
        }
        val spinner: AppCompatSpinner = fragmentView.findViewById(R.id.picrelease_spinner)
        val adapter = ArrayAdapter.createFromResource(requireContext(), R.array.picrelease_choices, android.R.layout.simple_spinner_item)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(preferenceService!!.profilePicRelease)
        spinner.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                val oldPosition = preferenceService!!.profilePicRelease
                preferenceService!!.profilePicRelease = position
                picReleaseConfImageView.visibility = if (position == PreferenceService.PROFILEPIC_RELEASE_SOME) View.VISIBLE else View.GONE
                if (position == PreferenceService.PROFILEPIC_RELEASE_SOME && position != oldPosition) {
                    launchProfilePictureRecipientsSelector(view)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        if (isDisabledProfilePicReleaseSettings) {
            fragmentView.findViewById<View>(R.id.picrelease_spinner_container).visibility = View.GONE
            fragmentView.findViewById<View>(R.id.picrelease_text).visibility = View.GONE
        }
        reloadNickname()

        return fragmentView
    }

    override fun onStart() {
        super.onStart()
        ListenerManager.smsVerificationListeners.add(smsVerificationListener)
        ListenerManager.profileListeners.add(profileListener)
    }

    override fun onStop() {
        ListenerManager.profileListeners.remove(profileListener)
        ListenerManager.smsVerificationListeners.remove(smsVerificationListener)
        super.onStop()
    }

    private fun updatePendingState(fragmentView: View?, force: Boolean) {
        logger.debug("*** updatePendingState")
        if (!requiredInstances()) {
            return
        }

        // update texts and enforce another update if the status of one value is pending
        if (updatePendingStateTexts(fragmentView) || force) {
            Thread(
                    CheckIdentityRoutine(userService) {
                        //update after routine
                        RuntimeUtil.runOnUiThread(Runnable { updatePendingStateTexts(fragmentView) })
                    }
            ).start()
        }
    }

    @SuppressLint("StaticFieldLeak")
    private fun updatePendingStateTexts(fragmentView: View?): Boolean {
        var pending = false
        logger.debug("*** updatePendingStateTexts")
        if (!requiredInstances()) {
            return false
        }
        if (!isAdded || isDetached || isRemoving) {
            return false
        }

        //update email linked text
        val linkedEmailText = fragmentView!!.findViewById<TextView>(R.id.linked_email)
        var email = userService!!.linkedEmail
        email = if (ThreemaApplication.EMAIL_LINKED_PLACEHOLDER == email) getString(R.string.unchanged) else email
        when (userService!!.emailLinkingState) {
            UserService.LinkingState_LINKED -> linkedEmailText.text = "$email (${getString(R.string.verified)})"
            UserService.LinkingState_PENDING -> {
                linkedEmailText.text = "$email (${getString(R.string.pending)})"
                pending = true
            }
            else -> linkedEmailText.text = getString(R.string.not_linked)
        }
        linkedEmailText.invalidate()

        //update mobile text
        val linkedMobileText = fragmentView.findViewById<TextView>(R.id.linked_mobile)

        // default
        linkedMobileText.text = getString(R.string.not_linked)
        var mobileNumber = userService!!.linkedMobile
        mobileNumber = if (ThreemaApplication.PHONE_LINKED_PLACEHOLDER == mobileNumber) getString(R.string.unchanged) else mobileNumber
        when (userService!!.mobileLinkingState) {
            UserService.LinkingState_LINKED -> if (mobileNumber != null) {
                val newMobileNumber = mobileNumber
                // lookup phone numbers asynchronously
                object : AsyncTask<TextView?, Void?, String?>() {
                    private var textView: TextView? = null
                    override fun doInBackground(vararg params: TextView?): String? {
                        textView = params[0]
                        if (isAdded && context != null) {
                            val verified = context!!.getString(R.string.verified)
                            return "${localeService!!.getHRPhoneNumber(newMobileNumber)} ($verified)"
                        }
                        return null
                    }

                    override fun onPostExecute(result: String?) {
                        if (isAdded && !isDetached && !isRemoving && context != null) {
                            textView!!.text = result
                        }
                    }
                }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, linkedMobileText)
            }
            UserService.LinkingState_PENDING -> {
                pending = true
                val newMobileNumber = userService!!.getLinkedMobile(true)
                if (newMobileNumber != null) {
                    object : AsyncTask<TextView?, Void?, String?>() {
                        private var textView: TextView? = null
                        override fun doInBackground(vararg params: TextView?): String? {
                            if (isAdded && context != null) {
                                textView = params[0]
                                return "${if (localeService != null) localeService!!.getHRPhoneNumber(newMobileNumber) else ""} (${context!!.getString(R.string.pending)})"
                            }
                            return null
                        }

                        override fun onPostExecute(result: String?) {
                            if (isAdded && !isDetached && !isRemoving && context != null) {
                                textView!!.text = result
                            }
                        }
                    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, linkedMobileText)
                }
            }
            else -> {
            }
        }
        linkedMobileText.invalidate()

        //revocation key
        val revocationKey = fragmentView.findViewById<TextView>(R.id.revocation_key_sum)
        object : AsyncTask<TextView?, Void?, String?>() {
            private var textView: TextView? = null
            override fun doInBackground(vararg params: TextView?): String? {
                if (isAdded) {
                    textView = params[0]
                    val revocationKeyLastSet = userService!!.lastRevocationKeySet
                    if (!isDetached && !isRemoving && context != null) {
                        return if (revocationKeyLastSet != null) {
                            context!!.getString(R.string.revocation_key_set_at, LocaleUtil.formatTimeStampString(context, revocationKeyLastSet.time, true))
                        } else {
                            context!!.getString(R.string.revocation_key_not_set)
                        }
                    }
                }
                return null
            }

            override fun onPostExecute(result: String?) {
                if (isAdded && !isDetached && !isRemoving && context != null) {
                    textView!!.text = result
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, revocationKey)
        return pending
    }

    private fun configureEditWithButton(l: RelativeLayout, button: ImageView, disable: Boolean) {
        if (disable) {
            button.visibility = View.INVISIBLE
        } else {
            button.setOnClickListener(this)
        }
    }

    private val identity: String
        get() {
            if (!requiredInstances()) {
                return "undefined"
            }
            return if (userService!!.hasIdentity()) {
                userService!!.identity
            } else {
                "undefined"
            }
        }

    private fun deleteIdentity() {
        if (!requiredInstances()) {
            return
        }
        DeleteIdentityAsyncTask(parentFragmentManager) { exitProcess(0) }.execute()
    }

    private fun setRevocationPassword() {
        val dialogFragment: DialogFragment = PasswordEntryDialog.newInstance(
                R.string.revocation_key_title,
                R.string.revocation_explain,
                R.string.password_hint,
                R.string.ok,
                R.string.cancel,
                8,
                MAX_REVOCATION_PASSWORD_LENGTH,
                R.string.backup_password_again_summary,
                0, 0)
        dialogFragment.setTargetFragment(this, 0)
        dialogFragment.show(parentFragmentManager, DIALOG_TAG_SET_REVOCATION_KEY)
    }

    override fun onClick(v: View) {
        var neutral: Int
        when (v.id) {
            R.id.change_email -> {
                neutral = 0
                if (userService!!.emailLinkingState != UserService.LinkingState_NONE) {
                    neutral = R.string.unlink
                }
                val textEntryDialog = TextEntryDialog.newInstance(
                        R.string.wizard2_email_linking,
                        R.string.wizard2_email_hint,
                        R.string.ok,
                        neutral,
                        R.string.cancel,
                        userService!!.linkedEmail,
                        InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, TextEntryDialog.INPUT_FILTER_TYPE_NONE)
                textEntryDialog.setTargetFragment(this, 0)
                textEntryDialog.show(fragmentManager, DIALOG_TAG_LINKED_EMAIL)
            }
            R.id.change_mobile -> {
                var presetNumber = serviceManager!!.localeService.getHRPhoneNumber(userService!!.linkedMobile)
                neutral = 0
                if (userService!!.mobileLinkingState != UserService.LinkingState_NONE) {
                    neutral = R.string.unlink
                } else {
                    presetNumber = localeService!!.countryCodePhonePrefix
                    if (!TestUtil.empty(presetNumber)) {
                        presetNumber += " "
                    }
                }
                val textEntryDialog1 = TextEntryDialog.newInstance(
                        R.string.wizard2_phone_linking,
                        R.string.wizard2_phone_hint,
                        R.string.ok,
                        neutral,
                        R.string.cancel,
                        presetNumber,
                        InputType.TYPE_CLASS_PHONE,
                        TextEntryDialog.INPUT_FILTER_TYPE_PHONE)
                textEntryDialog1.setTargetFragment(this, 0)
                textEntryDialog1.show(fragmentManager, DIALOG_TAG_LINKED_MOBILE)
            }
            R.id.revocation_key -> if (preferenceService!!.lockMechanism != PreferenceService.LockingMech_NONE) {
                HiddenChatUtil.launchLockCheckDialog(null, this, preferenceService, LOCK_CHECK_REVOCATION)
            } else {
                setRevocationPassword()
            }
            R.id.delete_id ->                // ask for pin before entering
                if (preferenceService!!.lockMechanism != PreferenceService.LockingMech_NONE) {
                    HiddenChatUtil.launchLockCheckDialog(null, this, preferenceService, LOCK_CHECK_DELETE_ID)
                } else {
                    confirmIdDelete()
                }
            R.id.export_id ->                // ask for pin before entering
                if (preferenceService!!.lockMechanism != PreferenceService.LockingMech_NONE) {
                    HiddenChatUtil.launchLockCheckDialog(null, this, preferenceService, LOCK_CHECK_EXPORT_ID)
                } else {
                    startActivity(Intent(context, ExportIDActivity::class.java))
                }
            R.id.picrelease_config -> launchProfilePictureRecipientsSelector(v)
            R.id.profile_edit -> {
                val nicknameEditDialog = TextEntryDialog.newInstance(R.string.set_nickname_title,
                        R.string.wizard3_nickname_hint,
                        R.string.ok, 0,
                        R.string.cancel,
                        userService!!.publicNickname,
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                        0,
                        ProtocolDefines.PUSH_FROM_LEN)
                nicknameEditDialog.setTargetFragment(this, 0)
                nicknameEditDialog.show(fragmentManager, DIALOG_TAG_EDIT_NICKNAME)
            }
            R.id.my_id_qr -> QRCodePopup(context, requireActivity().window.decorView, activity).show(v, null)
            R.id.avatar -> launchContactImageZoom(v)
            R.id.my_id_share -> ShareUtil.shareContact(context, null)
        }
    }

    private fun launchContactImageZoom(v: View) {
        if (view != null) {
            val rootView = requireView().findViewById<View>(R.id.main_content)
            if (fileService!!.hasContactAvatarFile(contactService!!.me)) {
                val detailPopup = ImagePopup(context, rootView, rootView.width, rootView.height)
                detailPopup.show(v, contactService!!.getAvatar(contactService!!.me, true), userService!!.publicNickname)
            }
        }
    }

    private fun launchProfilePictureRecipientsSelector(v: View) {
        AnimationUtil.startActivityForResult(activity, v, Intent(context, ProfilePicRecipientsActivity::class.java), 55)
    }

    private fun confirmIdDelete() {
        val dialogFragment: DialogFragment = GenericAlertDialog.newInstance(
                R.string.delete_id_title,
                R.string.delete_id_message,
                R.string.delete_id_title,
                R.string.cancel)
        (dialogFragment as GenericAlertDialog).targetFragment = this
        dialogFragment.show(parentFragmentManager, DIALOG_TAG_DELETE_ID)
    }

    @SuppressLint("StaticFieldLeak")
    private fun launchMobileVerification(normalizedPhoneNumber: String) {
        object : AsyncTask<Void?, Void?, String?>() {
            override fun doInBackground(vararg params: Void?): String? {
                try {
                    userService!!.linkWithMobileNumber(normalizedPhoneNumber)
                } catch (e: LinkMobileNoException) {
                    return e.message
                } catch (e: Exception) {
                    logger.error("Exception", e)
                    return e.message
                }
                return null
            }

            override fun onPostExecute(result: String?) {
                if (isAdded && !isDetached && !isRemoving && context != null) {
                    if (TestUtil.empty(result)) {
                        Toast.makeText(context, R.string.verification_started, Toast.LENGTH_LONG).show()
                    } else {
                        updatePendingStateTexts(view)
                        SimpleStringAlertDialog.newInstance(R.string.verify_title, result).show(parentFragmentManager, "ve")
                    }
                }
            }
        }.execute()
    }

    private fun reloadNickname() {
        nicknameTextView!!.text = if (!TestUtil.empty(userService!!.publicNickname)) userService!!.publicNickname else userService!!.identity
    }

    @SuppressLint("StaticFieldLeak")
    private fun setRevocationKey(text: String) {
        object : AsyncTask<Void?, Void?, Boolean>() {
            override fun onPreExecute() {
                GenericProgressDialog.newInstance(R.string.revocation_key_title, R.string.please_wait).show(parentFragmentManager, DIALOG_TAG_REVOKING)
            }

            override fun doInBackground(vararg voids: Void?): Boolean {
                try {
                    return userService!!.setRevocationKey(text)
                } catch (x: Exception) {
                    logger.error("Exception", x)
                }
                return false
            }

            override fun onPostExecute(success: Boolean) {
                updatePendingStateTexts(view)
                DialogUtil.dismissDialog(parentFragmentManager, DIALOG_TAG_REVOKING, true)
                if (!success) {
                    Toast.makeText(context, getString(R.string.error) + ": " + getString(R.string.revocation_key_not_set), Toast.LENGTH_LONG).show()
                }
            }
        }.execute()
    }

    /*
     * DialogFragment callbacks
     */
    override fun onYes(tag: String, data: Any) {
        when (tag) {
            DIALOG_TAG_DELETE_ID -> {
                val dialogFragment = GenericAlertDialog.newInstance(
                        R.string.delete_id_title,
                        R.string.delete_id_message2,
                        R.string.delete_id_title,
                        R.string.cancel)
                dialogFragment.targetFragment = this
                dialogFragment.show(parentFragmentManager, DIALOG_TAG_REALLY_DELETE)
            }
            DIALOG_TAG_REALLY_DELETE -> deleteIdentity()
            DIALOG_TAG_LINKED_MOBILE_CONFIRM -> launchMobileVerification(data as String)
            else -> {
            }
        }
    }

    override fun onNo(tag: String, data: Any) {}
    override fun onYes(tag: String, text: String) {
        when (tag) {
            DIALOG_TAG_LINKED_MOBILE -> {
                val normalizedPhoneNumber = localeService!!.getNormalizedPhoneNumber(text)
                val alertDialog = GenericAlertDialog.newInstance(R.string.wizard2_phone_number_confirm_title, String.format(getString(R.string.wizard2_phone_number_confirm), normalizedPhoneNumber), R.string.ok, R.string.cancel)
                alertDialog.setData(normalizedPhoneNumber)
                alertDialog.targetFragment = this
                alertDialog.show(parentFragmentManager, DIALOG_TAG_LINKED_MOBILE_CONFIRM)
            }
            DIALOG_TAG_LINKED_EMAIL -> LinkWithEmailAsyncTask(context, parentFragmentManager, text) { updatePendingStateTexts(view) }.execute()
            DIALOG_TAG_EDIT_NICKNAME -> {
                // Update public nickname
                if (text != userService!!.publicNickname) {
                    userService!!.publicNickname = if ("" == text.trim { it <= ' ' }) userService!!.identity else text
                }
                reloadNickname()
            }
            else -> {
            }
        }
    }

    override fun onYes(tag: String, text: String, isChecked: Boolean, data: Any) {
        when (tag) {
            DIALOG_TAG_SET_REVOCATION_KEY -> setRevocationKey(text)
        }
    }

    override fun onNo(tag: String) {}
    override fun onNeutral(tag: String) {
        when (tag) {
            DIALOG_TAG_LINKED_MOBILE -> Thread {
                try {
                    userService!!.unlinkMobileNumber()
                } catch (e: Exception) {
                    LogUtil.exception(e, activity)
                } finally {
                    RuntimeUtil.runOnUiThread { updatePendingStateTexts(view) }
                }
            }.start()
            DIALOG_TAG_LINKED_EMAIL -> LinkWithEmailAsyncTask(context, parentFragmentManager, "") { updatePendingStateTexts(view) }.execute()
            else -> {
            }
        }
    }

    protected fun requiredInstances(): Boolean {
        if (!checkInstances()) {
            this.instantiate()
        }
        return checkInstances()
    }

    protected fun checkInstances(): Boolean {
        return TestUtil.required(
                serviceManager,
                fileService,
                userService,
                preferenceService,
                localeService,
                fingerPrintService)
    }

    protected fun instantiate() {
        serviceManager = ThreemaApplication.getServiceManager()
        if (serviceManager != null) {
            try {
                contactService = serviceManager!!.contactService
                userService = serviceManager!!.userService
                fileService = serviceManager!!.fileService
                preferenceService = serviceManager!!.preferenceService
                localeService = serviceManager!!.localeService
                fingerPrintService = serviceManager!!.fingerPrintService
            } catch (e: MasterKeyLockedException) {
                logger.debug("Master Key locked!")
            } catch (e: ThreemaException) {
                logger.error("Exception", e)
            }
        }
    }

    fun onLogoClicked() {
        val scrollView: NestedScrollView = requireView().findViewById(R.id.fragment_id_container)
        scrollView.scrollTo(0, 0)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && hidden != this.hidden) {
            updatePendingState(view, false)
        }
        this.hidden = hidden
    }

    override fun onSaveInstanceState(outState: Bundle) {
        logger.info("saveInstance")
        super.onSaveInstanceState(outState)
    }

    /* callbacks from AvatarEditView */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (avatarView != null) {
            avatarView!!.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        when (requestCode) {
            ThreemaActivity.ACTIVITY_ID_VERIFY_MOBILE -> {
                @Suppress("ControlFlowWithEmptyBody")
                if (resultCode != Activity.RESULT_OK) {
                    // TODO: make sure its status is unlinked if linking failed
                }
                updatePendingState(view, false)
            }
            LOCK_CHECK_DELETE_ID -> if (resultCode == Activity.RESULT_OK) {
                confirmIdDelete()
            }
            LOCK_CHECK_EXPORT_ID -> if (resultCode == Activity.RESULT_OK) {
                startActivity(Intent(context, ExportIDActivity::class.java))
            }
            LOCK_CHECK_REVOCATION -> if (resultCode == Activity.RESULT_OK) {
                setRevocationPassword()
            }
            else -> if (avatarView != null) {
                avatarView!!.onActivityResult(requestCode, resultCode, intent)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MyIDFragment::class.java)
        private const val MAX_REVOCATION_PASSWORD_LENGTH = 256
        private const val LOCK_CHECK_REVOCATION = 33
        private const val LOCK_CHECK_DELETE_ID = 34
        private const val LOCK_CHECK_EXPORT_ID = 35
        private const val DIALOG_TAG_EDIT_NICKNAME = "cedit"
        private const val DIALOG_TAG_SET_REVOCATION_KEY = "setRevocationKey"
        private const val DIALOG_TAG_LINKED_EMAIL = "linkedEmail"
        private const val DIALOG_TAG_LINKED_MOBILE = "linkedMobile"
        private const val DIALOG_TAG_REALLY_DELETE = "reallyDeleteId"
        private const val DIALOG_TAG_DELETE_ID = "deleteId"
        private const val DIALOG_TAG_LINKED_MOBILE_CONFIRM = "cfm"
        private const val DIALOG_TAG_REVOKING = "revk"
    }
}
