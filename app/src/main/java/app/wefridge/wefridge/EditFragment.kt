package app.wefridge.wefridge

import android.Manifest
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import app.wefridge.wefridge.databinding.FragmentEditBinding
import app.wefridge.wefridge.datamodel.*
import app.wefridge.wefridge.datamodel.Unit
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.GeoPoint
import kotlinx.android.synthetic.main.fragment_edit.*
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

/**
 * A simple [Fragment] subclass.
 * Use the [EditFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class EditFragment : Fragment() {
    private var _binding: FragmentEditBinding? = null
    private val binding get() = _binding!!
    private var model: Item? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private val ADD_ITEM_MODE: Boolean get() = model?.firebaseId.isNullOrEmpty()
    private var location: GeoPoint? = null
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            model = it.getParcelable(ARG_MODEL)
        }


        (requireActivity() as AppCompatActivity).supportActionBar?.title =
            model?.name ?: getString(R.string.add_new_item)

        // this piece of code is partially based on https://developer.android.com/training/permissions/requesting#kotlin
        requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
                if (isGranted) {
                    getCurrentLocation()
                } else {
                    Log.d("EditFragment", "Request for location access denied.")
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // this line of code was partially inspired by https://stackoverflow.com/questions/11741270/android-sharedpreferences-in-fragment
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireActivity())

        // Inflate the layout for this fragment
        _binding = FragmentEditBinding.inflate(inflater, container, false)

        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adaptUIToModel()
        hideDatePicker()
        setLocationPickerActivation()
        setUpOnClickListenersForFormComponents()
        setUpSaveMechanism()
        setUpOnDateChangedListenerForDatePicker()
        setUpOnFocusChangeListenerForAddressInputEditText()
        location = model?.location
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!ADD_ITEM_MODE) {  // **new** Items should not be saved automatically, i. e. onDestroy
            // TODO: put the following proofing into a separate function
            if (location == null && model?.isShared == true) {
                model?.location = null
                model?.geohash = null
                model?.isShared = false
                displayAlertOnSaveSharedItemWithoutLocation()
            } else {
                setModelLocationAttribute()
                setModelGeohashAttribute()
            }
            setModelContactNameAttribute()
            setModelContactEmailAttribute()

            // TODO: put the following proofing into a separate function
            if ((model?.contactEmail == null || model?.contactEmail == "") && model?.isShared == true) {
                model?.isShared = false
                displayAlertOnSaveSharedItemWithoutContactEmail()
            }

            val itemController: ItemControllerInterface = ItemController()
            itemController.saveItem(model!!, { /* do nothing on success */ }, { displayAlertOnSaveItemFailed() })
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setUpOnClickListenersForFormComponents() {
        locateMeButton.setOnClickListener { getCurrentLocation() }

        itemSaveButton.setOnClickListener { itemAddressTextInputLayout.clearFocus(); saveNewItem() }

        itemIsSharedSwitch.setOnClickListener { setLocationPickerActivation() }

        itemBestByDateTextInputLayout.editText?.setOnClickListener {
            setDatePickerVisibility()
            model?.bestByDate?.let { setDatePickerDateTo(it) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setUpSaveMechanism() {
        if (!ADD_ITEM_MODE) setUpOnChangedListeners()
        else setUpSaveButton()
    }

    private fun setUpSaveButton() {
        itemSaveButton.setOnClickListener { saveNewItem() }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setUpOnDateChangedListenerForDatePicker() {
        itemBestByDatePicker.setOnDateChangedListener { _, _, _, _ -> setDateStringToBestByDateEditText(); setModelBestByDateAttribute() }

    }

    private fun setUpOnFocusChangeListenerForAddressInputEditText() {
        itemAddressTextInputLayout.editText?.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) location = getGeoPointFromAddressUserInput()
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun setUpOnChangedListeners() {
        itemNameTextInputLayout.editText?.addTextChangedListener { setModelNameAttribute() }
        itemQuantityTextInputLayout.editText?.addTextChangedListener { setModelQuantityAttribute() }
        itemUnitRadioGroup.setOnCheckedChangeListener { _, _ -> setModelUnitAttribute() }
        // itemBestByDateTextInputLayout.editText?.addTextChangedListener { setModelBestByDateAttribute() }
        itemIsSharedSwitch.setOnCheckedChangeListener { _, _ -> setModelIsSharedAttribute() }
        itemDescriptionTextInputLayout.editText?.addTextChangedListener { setModelDescriptionAttribute() }
        // TODO: what about the location? where do we store that? Also in the item?
    }

    private fun setModelNameAttribute() {
        model?.name = itemNameTextInputLayout.editText?.text.toString()
    }

    private fun setModelQuantityAttribute() {
        val quantityString = itemQuantityTextInputLayout.editText?.text.toString()
        model?.quantity = if (quantityString.isEmpty() || quantityString == "null") null else quantityString.toInt()

    }

    private fun setModelUnitAttribute() {
        model?.unit = matchRadioButtonIdToUnit()
    }

    private fun setModelLocationAttribute() {
        model?.location = location
    }

    private fun setModelGeohashAttribute() {
        if (location != null)
            model?.geohash = GeoFireUtils.getGeoHashForLocation(GeoLocation(location!!.latitude, location!!.longitude))
    }

    private fun setModelContactNameAttribute() {
        val userNameAsFallbackValue = FirebaseAuth.getInstance().currentUser?.displayName
        model?.contactName = sharedPreferences.getString(SETTINGS_NAME, userNameAsFallbackValue)
    }

    private fun setModelContactEmailAttribute() {
        val userEmailAsFallbackValue = FirebaseAuth.getInstance().currentUser?.email
        model?.contactEmail = sharedPreferences.getString(SETTINGS_EMAIL, userEmailAsFallbackValue)
    }

    private fun setModelBestByDateAttribute() {
        if (itemBestByDateTextInputLayout.editText?.text.toString() != "")
            model?.bestByDate = getDateFromDatePicker()
        else
            model?.bestByDate = null
    }

    private fun setModelIsSharedAttribute() {
        model?.isShared = itemIsSharedSwitch.isChecked
    }


    private fun setModelDescriptionAttribute() {
        model?.description = itemDescriptionTextInputLayout.editText?.text.toString()
    }

    private fun saveNewItem() {
        // TODO: put the following proofing into a separate function
        if (itemIsSharedSwitch.isChecked && location == null) {
            displayAlertOnSaveSharedItemWithoutLocation()
            itemAddressTextInputLayout.editText?.setText("")
        } else {

            val ownerController: OwnerControllerInterface = OwnerController()
            ownerController.getCurrentUser { ownerDocumentReference ->
                model = Item(
                    ownerReference = ownerDocumentReference
                )
                setModelNameAttribute()
                setModelQuantityAttribute()
                setModelUnitAttribute()
                setModelBestByDateAttribute()
                setModelIsSharedAttribute()
                setModelLocationAttribute()
                setModelGeohashAttribute()
                setModelDescriptionAttribute()
                setModelContactNameAttribute()
                setModelContactEmailAttribute()

                // TODO: put the following proofing into a separate function
                if ((model?.contactEmail == null || model?.contactEmail == "") && model?.isShared == true) {
                    model?.isShared = false
                    displayAlertOnSaveSharedItemWithoutContactEmail()
                } else {

                    val itemController: ItemControllerInterface = ItemController()
                    itemController.saveItem(model!!, {
                        // saving was successful
                        Toast.makeText(requireContext(), "Item saved", Toast.LENGTH_SHORT).show()

                        // this line of code is based on https://www.codegrepper.com/code-examples/kotlin/android+go+back+to+previous+activity+programmatically
                        activity?.onBackPressed()
                    },
                        {
                            // saving newItem failed
                            displayAlertOnSaveItemFailed()
                        })

                }
            }

        }
    }


    private fun getCurrentLocation() {

        // this piece of code is partially based on https://developer.android.com/training/permissions/requesting#kotlin
        when {
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                // called when permission was granted
                fusedLocationClient =
                    LocationServices.getFusedLocationProviderClient(requireContext())
                fusedLocationClient.getCurrentLocation(
                    PRIORITY_HIGH_ACCURACY,
                    CancellationTokenSource().token
                )
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            itemAddressTextInputLayout.editText?.requestFocus()
                            setAddressStringToItemAddressTextEdit(GeoPoint(location.latitude, location.longitude))
                            itemAddressTextInputLayout.editText?.clearFocus()
                        } else {
                            displayAlertDialogOnFailedLocationDetermination()
                        }
                    }

                    .addOnFailureListener {
                        displayAlertDialogOnPermissionDenied()
                    }

            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                // called when permission denied
                displayAlertDialogOnFailedLocationDetermination()
            }
            else -> {
                // called when permission settings unspecified (like "ask every time")
                requestPermissionLauncher.launch(
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
        }

    }

    private fun setAddressStringToItemAddressTextEdit(location: GeoPoint) {
        // the following code is based on https://stackoverflow.com/questions/9409195/how-to-get-complete-address-from-latitude-and-longitude

        itemAddressTextInputLayout.editText?.setText(buildAddressString(location))
    }

    private fun getGeoPointFromAddressUserInput(): GeoPoint? {

        // the following piece of code is inspired by https://stackoverflow.com/questions/3574644/how-can-i-find-the-latitude-and-longitude-from-address/27834110#27834110
        val userInputAddress = itemAddressTextInputLayout.editText?.text.toString()
        val geocoder = Geocoder(requireContext())

        if (userInputAddress == "") return null
        val matchedAddresses: List<Address> = geocoder.getFromLocationName(userInputAddress, 1)
        if (matchedAddresses.isEmpty()) return null


        val chosenAddress = matchedAddresses[0]

        return GeoPoint(chosenAddress.latitude, chosenAddress.longitude)

    }

    private fun buildAddressString(location: GeoPoint): String {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        val address =
            geocoder.getFromLocation(location.latitude, location.longitude, 1)
        val addressLine = address.get(0).getAddressLine(0)
        val city = address.get(0).locality
        val state = address.get(0).adminArea
        val postalCode = address.get(0).postalCode

        return "${addressLine}, ${postalCode} ${city}, ${state}"
    }

    private fun displayAlertOnSaveSharedItemWithoutContactEmail() {
        AlertDialog.Builder(requireContext())
            .setTitle("Contact email missing")
            .setMessage("In order to share an Item, you have to provide a contact email other users can use to reach out to you. Please state your email address in the settings tab.")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun displayAlertOnSaveSharedItemWithoutLocation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Please specify a valid address")
            .setMessage("The address you've entered couldn't be matched to a real location. Please specify a valid address or turn off the sharing option.")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun displayAlertOnSaveItemFailed() {
        AlertDialog.Builder(requireContext())
            .setTitle("Error while saving your foodstuff")
            .setMessage("Please check your internet connection and try again.")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun displayAlertDialogOnPermissionDenied() {
        AlertDialog.Builder(requireContext())
            .setTitle("Unable to determine location")
            .setMessage("Please try it another time.")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun displayAlertDialogOnFailedLocationDetermination() {
        // TODO: outsource strings to strings file
        // this piece of code is based on https://stackoverflow.com/questions/2115758/how-do-i-display-an-alert-dialog-on-android
        AlertDialog.Builder(requireContext())
            .setTitle("Permission denied")
            .setMessage("We have no permission to access your location.\nIf you want to make use of the \"locate me\" functionality, please enable location access in settings.")
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton("Open settings") { dialogInterface: DialogInterface, i: Int ->
                // this piece of code is based on https://stackoverflow.com/questions/19517417/opening-android-settings-programmatically
                dialogInterface.run { startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            }
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun adaptUIToModel() {
        hideItemSaveButtonOnExistingModelId()
        fillFieldsWithModelContent()
    }

    private fun hideItemSaveButtonOnExistingModelId() {
        if (!ADD_ITEM_MODE) itemSaveButton?.isVisible = false
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun fillFieldsWithModelContent() {
        itemNameTextInputLayout.editText?.setText(model?.name)
        model?.quantity?.toString()?.let { itemQuantityTextInputLayout.editText?.setText(it) }
        matchUnitValueToRadioButtonId()?.let { itemUnitRadioGroup.check(it) }
        model?.bestByDate?.let { setDatePickerDateTo(it); setDateStringToBestByDateEditText() }
        itemIsSharedSwitch.isChecked = model?.isShared ?: false
        model?.location?.let { itemAddressTextInputLayout.editText?.setText(buildAddressString(it)) }
        itemDescriptionTextInputLayout.editText?.setText(model?.description)
    }

    private fun matchUnitValueToRadioButtonId(): Int? {
        return when (model?.unit?.value) {
            Unit.GRAM.value -> radio_button_gram.id
            Unit.KILOGRAM.value -> radio_button_kilogram.id
            Unit.LITER.value -> radio_button_liter.id
            Unit.MILLILITER.value -> radio_button_milliliter.id
            Unit.OUNCE.value -> radio_button_ounce.id
            Unit.PIECE.value -> radio_button_piece.id
            else -> null
        }
    }

    private fun matchRadioButtonIdToUnit(): Unit? {
        return when (itemUnitRadioGroup.checkedRadioButtonId) {
            radio_button_gram.id -> Unit.GRAM
            radio_button_kilogram.id -> Unit.KILOGRAM
            radio_button_liter.id -> Unit.LITER
            radio_button_milliliter.id -> Unit.MILLILITER
            radio_button_ounce.id -> Unit.OUNCE
            radio_button_piece.id -> Unit.PIECE
            else -> null
        }
    }



    private fun setDateStringToBestByDateEditText() {
        itemBestByDateTextInputLayout.editText?.setText(buildDateStringFromDatePicker())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setDatePickerDateTo(date: Date) {
        val bestByDate = convertToLocalDate(date)
        itemBestByDatePicker.updateDate(bestByDate.year, bestByDate.monthValue - 1, bestByDate.dayOfMonth)

    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun convertToLocalDate(date: Date): LocalDate {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    }

    private fun buildDateStringFromModelBestByDate(): String {
        return DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(model?.bestByDate) ?: ""
    }

    private fun buildDateStringFromDatePicker(): String {
        val day = itemBestByDatePicker.dayOfMonth
        val month = itemBestByDatePicker.month
        val year = itemBestByDatePicker.year
        val calendar = Calendar.getInstance()
        calendar.set(year, month, day)



        return DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(calendar.time)
    }

    private fun getDateFromDatePicker(): Date {
        val day = itemBestByDatePicker.dayOfMonth
        val month = itemBestByDatePicker.month
        val year = itemBestByDatePicker.year

        val calendar = Calendar.getInstance()
        calendar.set(year, month, day)

        return calendar.time
    }

    private fun setDatePickerVisibility() {
        when (itemBestByDatePicker.visibility) {
            View.GONE -> showDatePicker()
            View.INVISIBLE -> showDatePicker()
            View.VISIBLE -> hideDatePicker()
        }

    }

    private fun showDatePicker() {
        itemBestByDatePicker.visibility = View.VISIBLE
    }

    private fun hideDatePicker() {
        itemBestByDatePicker.visibility = View.GONE
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setLocationPickerActivation() {
        if (itemIsSharedSwitch.isChecked) {
            activateLocationPickerElements()
        } else {
            deactivateLocationPickerElements()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun deactivateLocationPickerElements() {
        locateMeButton.isClickable = false
        locateMeButton.alpha = .5f

        itemAddressTextInputLayout.editText?.isEnabled = false
        itemAddressTextInputLayout.editText?.focusable = View.NOT_FOCUSABLE
        itemAddressTextInputLayout.editText?.isFocusableInTouchMode = false
        itemAddressTextInputLayout.editText?.inputType = InputType.TYPE_NULL
        itemAddressTextInputLayout.alpha = .5f


        itemDescriptionTextInputLayout.editText?.isEnabled = false
        itemDescriptionTextInputLayout.editText?.focusable = View.NOT_FOCUSABLE
        itemDescriptionTextInputLayout.editText?.isFocusableInTouchMode = false
        itemDescriptionTextInputLayout.editText?.inputType = InputType.TYPE_NULL
        itemDescriptionTextInputLayout.alpha = .5f

    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun activateLocationPickerElements() {
        locateMeButton.isClickable = true
        locateMeButton.alpha = 1f

        itemAddressTextInputLayout.editText?.isEnabled = true
        itemAddressTextInputLayout.editText?.focusable = View.FOCUSABLE
        itemAddressTextInputLayout.editText?.isFocusableInTouchMode = true
        itemAddressTextInputLayout.editText?.inputType = InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE
        itemAddressTextInputLayout.alpha = 1f


        itemDescriptionTextInputLayout.editText?.isEnabled = true
        itemDescriptionTextInputLayout.editText?.focusable = View.FOCUSABLE
        itemDescriptionTextInputLayout.editText?.isFocusableInTouchMode = true
        itemDescriptionTextInputLayout.editText?.inputType =
            InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE
        itemDescriptionTextInputLayout.alpha = 1f
    }


    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment EditFragment.
         */
        @JvmStatic
        fun newInstance(model: Item) =
            EditFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_MODEL, model)
                }
            }
    }
}