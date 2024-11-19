package com.singhez.weather

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.*
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.singhez.weather.models.WeatherResponse
import com.singhez.weather.network.weatherService
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

open class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    private val REQUEST_CHECK_SETTINGS = 199

    private lateinit var sharedPreferences: SharedPreferences

    var progressDialog : Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        toolBarMainActivity.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_refresh -> {
                    getLocation()
                    true
                }
                else -> false
            }
        }

        sharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        setupUI()

        if (!isLocationEnabled()){
            enableLocation()
        }else{
            askForLocationPermission()
        }

    }


    private fun getLocation(){
        showProgressDialog()
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager?

        val locationListener = object : LocationListener{
            override fun onLocationChanged(location: Location) {
                val latitude = location.latitude
                val longitude = location.longitude
                getLocationWeatherDetails(latitude,longitude)
            }

        }

        try {
            locationManager!!.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, locationListener)
        } catch (ex:SecurityException) {
            ex.printStackTrace()
        }
    }

    private fun getLocationWeatherDetails(latitude:Double, longitude:Double){

        if (isNetworkAvailable()){

            val retrofit: Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service : weatherService = retrofit.create(weatherService::class.java)

            val listCall: Call<WeatherResponse> = service.weather(latitude,longitude,Constants.METRIC_UNIT,Constants.APP_ID)


            listCall.enqueue(object: Callback<WeatherResponse>{
                override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                    dismissProgressDialog()
                    if (response.isSuccessful){
                        val weatherList = response.body()
                       // Log.d("weatherList", "${weatherList!!.weather}")
                        if (weatherList != null) {
                            val weatherResponseJsonString = Gson().toJson(weatherList)
                            val editor = sharedPreferences.edit()
                            editor.putString(Constants.WEATHER_RESPONSE_DATA,weatherResponseJsonString)
                            editor.apply()
                            setupUI()
                        }
                    }else{
                        when(response.code()){
                            400 -> {Log.e("Error 400", "Bad connection")}
                            404 -> {Log.e("Error 404","Not found")}
                            else -> {Log.e("Error", "Generic Error")}
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    dismissProgressDialog()
                    Log.e("Errorrrrr", t.message.toString())
                }
            })

        }else
            Toast.makeText(this, "Please connect to internet", Toast.LENGTH_LONG).show()

    }

    private fun setupUI(){

        val weatherResponseJsonString = sharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA,"")
        if (!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)



            for (i in weatherList.weather.indices){
                tvMain.text = weatherList.weather[i].main
                tvMainDescription.text = weatherList.weather[i].description


                when(weatherList.weather[i].icon){
                    "01d" -> ivMain.setImageResource(R.drawable.ic_sunny)
                    "02d" -> ivMain.setImageResource(R.drawable.ic_cloud)
                    "03d" -> ivMain.setImageResource(R.drawable.ic_cloud)
                    "04d" -> ivMain.setImageResource(R.drawable.ic_cloud)
                    "04n" -> ivMain.setImageResource(R.drawable.ic_cloud)
                    "10d" -> ivMain.setImageResource(R.drawable.ic_rain)
                    "11d" -> ivMain.setImageResource(R.drawable.ic_storm)
                    "13d" -> ivMain.setImageResource(R.drawable.ic_snowflake)
                    "01n" -> ivMain.setImageResource(R.drawable.ic_cloud)
                    "02n" -> ivMain.setImageResource(R.drawable.ic_cloud)
                    "03n" -> ivMain.setImageResource(R.drawable.ic_cloud)
                    "10n" -> ivMain.setImageResource(R.drawable.ic_cloud)
                    "11n" -> ivMain.setImageResource(R.drawable.ic_rain)
                    "13n" -> ivMain.setImageResource(R.drawable.ic_snowflake)
                    "09n" -> ivMain.setImageResource(R.drawable.ic_rain)
                    "09d" -> ivMain.setImageResource(R.drawable.ic_rain)
                    "50d" -> ivMain.setImageResource(R.drawable.ic_mist)
                    "50n" -> ivMain.setImageResource(R.drawable.ic_mist)
                }


            }

            tvTemperatureValue.text = weatherList.main.temp.toString() + " " + getUnit(application.resources.configuration.toString())
            tvWindValue.text = weatherList.wind.speed.toString() + " km/hr"
            tvHumidityValue.text = weatherList.main.humidity.toString() + " %"
            tvName.text = weatherList.name
            tvCountry.text = weatherList.sys.country
            tvPressureValue.text = weatherList.main.pressure.toString() + " hPa"
            tvSunriseTime.text = getNormalTime(weatherList.sys.sunrise) + " AM"
            tvSunsetTime.text = getNormalTime(weatherList.sys.sunset) + " PM"



        }


    }

    private fun getNormalTime(epochTime : Long) : String?{
        val date = Date(epochTime * 1000L)
        val sdf = SimpleDateFormat("hh:mm")
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

    private fun getUnit(value : String): String?{
        var value = "°C"
        if ( value == "US" || value == "LR" || value == "MM"){
            value = "°F"
        }
        return value
    }

    private fun showProgressDialog(){
        progressDialog = Dialog(this)
        progressDialog!!.setContentView(R.layout.custom_progress_dialog)
        progressDialog!!.setCancelable(false)
        progressDialog!!.show()
    }

    private fun dismissProgressDialog(){
        if (progressDialog!= null){
            progressDialog!!.dismiss()
        }
    }

    private fun askForLocationPermission(){
        Dexter.withContext(this).withPermissions(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
        ).withListener(object : MultiplePermissionsListener{
            override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                if (report!!.areAllPermissionsGranted()){

                    getLocation()

                }

                if (report.isAnyPermissionPermanentlyDenied){
                    Toast.makeText(this@MainActivity,
                    "Please allow the permission, because it's mandatory for the app to work",
                    Toast.LENGTH_LONG).show()
                }
            }

            override fun onPermissionRationaleShouldBeShown(
                    p0: MutableList<PermissionRequest>?,
                    p1: PermissionToken?) {

                showRationalDialogForPermission()

            }

        }).onSameThread().check()
    }

    private fun showRationalDialogForPermission() {
        AlertDialog.Builder(this).
        setMessage("It looks like you denied permissions. Please allow them, because it's mandatory for the app to work")
                .setPositiveButton("GO TO SETTINGS")
                { dialogInterface: DialogInterface, i: Int ->
                    try {
                        val intent =  Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        val uri = Uri.fromParts("package", packageName, null)
                        intent.data = uri
                        startActivity(intent)
                    } catch (e: ActivityNotFoundException){
                        e.printStackTrace()
                    }
                }
                .setNegativeButton("Cancel")
                { dialogInterface: DialogInterface, i: Int ->
                    dialogInterface.dismiss()
                }.show()
    }

    private fun isLocationEnabled(): Boolean{
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun enableLocation() {

        val locationRequest = LocationRequest.create()?.apply {
            priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val builder = LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest!!)

        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            // All location settings are satisfied. The client can initialize
            // location requests here.
            // ...
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException){
                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    exception.startResolutionForResult(this@MainActivity,
                            REQUEST_CHECK_SETTINGS)
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                }
            }
        }

   }

    private fun isNetworkAvailable():Boolean{
        val connectivityManager = this.getSystemService(
            Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network)?:return false

        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CHECK_SETTINGS -> when (resultCode) {
                Activity.RESULT_OK -> askForLocationPermission()
                Activity.RESULT_CANCELED -> Toast.makeText(this,
                        "Please enable location to see more accurate results",Toast.LENGTH_LONG).show()
            }
        }
    }

}





