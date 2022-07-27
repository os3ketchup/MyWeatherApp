package uz.os3ketchup.myweatherapp

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.dynamic.IFragmentWrapper
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.karumi.dexter.listener.single.PermissionListener
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import uz.os3ketchup.my_models.Constants
import uz.os3ketchup.my_models.Constants.APP_ID
import uz.os3ketchup.my_models.Constants.BASE_URL
import uz.os3ketchup.my_models.Constants.METRIC_UNIT
import uz.os3ketchup.my_models.Constants.PREFERENCE_NAME
import uz.os3ketchup.my_models.Constants.WEATHER_RESPONSE_DATA
import uz.os3ketchup.my_models.MyWeatherResponse
import uz.os3ketchup.myweatherapp.databinding.ActivityMainBinding
import uz.os3ketchup.network.WeatherService
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var mFusedLocationClient:FusedLocationProviderClient
    private lateinit var mSharedPreferences:SharedPreferences
    private var mProgressDialog:Dialog? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mSharedPreferences = getSharedPreferences(PREFERENCE_NAME, MODE_PRIVATE)
        setupUI()

        if (!isLocationEnabled()){
            Toast.makeText(this, "Your location provider is turned off. Please turned it on", Toast.LENGTH_SHORT).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            Dexter.withContext(this)
                .withPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(object : MultiplePermissionsListener, PermissionListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                            if (report!!.areAllPermissionsGranted()){
                                            requestLocationData()
                            }

                            if (report.isAnyPermissionPermanentlyDenied){
                                Toast.makeText(this@MainActivity, "You denied permission. Please enable them as it is mandatory for the app to work ", Toast.LENGTH_SHORT).show()
                            }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        p0: MutableList<PermissionRequest>?,
                        p1: PermissionToken?
                    ) {

                            showRationalDialogForPermission()
                    }

                    override fun onPermissionGranted(p0: PermissionGrantedResponse?) {

                    }

                    override fun onPermissionDenied(p0: PermissionDeniedResponse?) {

                    }

                    override fun onPermissionRationaleShouldBeShown(
                        p0: PermissionRequest?,
                        p1: PermissionToken?
                    ) {

                    }
                }).onSameThread()
                .check()
        }


    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_refresh,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId){
            R.id.action_refresh->{
                requestLocationData()

            }
        }
        return super.onOptionsItemSelected(item)
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {
        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest,
            mLocationCallback,
            Looper.myLooper()
        )
    }
    private val mLocationCallback = object :LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            val mLastLocation = locationResult.lastLocation
            val latitude = mLastLocation?.latitude!!
            Log.i("Current Latitude", "$latitude ")
            val longitude  = mLastLocation.longitude
            Log.i("Current longitude", "$longitude ")
            getLocationWeatherDetails(latitude,longitude)
        }
    }

    private fun showRationalDialogForPermission() {
        AlertDialog.Builder(this).setMessage("It looks like you have turned off permissions required for this future. It can be enabled under Application Settings ")
            .setPositiveButton("GO TO SETTINGS"){
                _,_->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri  = Uri.fromParts("package",packageName,null)
                    intent.data = uri
                    startActivity(intent)
                }catch (e:ActivityNotFoundException){
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel"){
                dialog,_->
                dialog.dismiss()
            }.show()

    }

    /*** location state true or false***/
    private fun isLocationEnabled():Boolean{
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)||locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun getLocationWeatherDetails(latitude:Double,longitude:Double){
        if (Constants.isNetworkAvailable(this)){
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val service = retrofit.create(WeatherService::class.java)
            val listCall = service.getWeather(
                latitude,longitude, METRIC_UNIT, APP_ID
            )
            showCustomDialog()


            listCall.enqueue(object :Callback<MyWeatherResponse>{
                @SuppressLint("CommitPrefEdits")
                override fun onResponse(
                    call: Call<MyWeatherResponse>,
                    response: Response<MyWeatherResponse>
                ) {

                    if (response.isSuccessful){
                        hideProgressDialog()
                        val weatherList = response.body()!!

                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(WEATHER_RESPONSE_DATA,weatherResponseJsonString)
                        editor.apply()
                        setupUI()
                        Log.i("Response result", "$weatherList ")
                    }else{

                        when(response.code()){
                            400->{
                                Log.e("Error 400", "Bad request")
                            }
                            404->{
                                Log.e("Error 404", "Not found")
                            }else->{
                            Log.e("Error", "Generic Error")
                            }
                            
                        }
                    }
                }

                override fun onFailure(call: Call<MyWeatherResponse>, t: Throwable) {
                    hideProgressDialog()
                    Log.e("Failure: ", t.message.toString())
                }
            })

        }else{
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCustomDialog(){
        mProgressDialog = Dialog(this)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()
    }
    private fun hideProgressDialog(){
        if (mProgressDialog!=null){
            mProgressDialog!!.dismiss()
        }
    }
    @SuppressLint("SetTextI18n", "NewApi")
    private fun setupUI(){

        val weatherResponseJsonString = mSharedPreferences.getString(WEATHER_RESPONSE_DATA,"")
        if (!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList = Gson().fromJson(weatherResponseJsonString,MyWeatherResponse::class.java)
            for (i in weatherList.weather.indices){
                binding.tvMain.text = weatherList.weather[i].main
                binding.tvDescription.text = weatherList.weather[i].description
                binding.tvDegree.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales .toString())
                binding.tvPerCent.text = weatherList.main.humidity.toString() + " %"
                binding.tvMaximum.text ="Max temp " +  weatherList.main.temp_max.toString()
                binding.tvMinimum.text ="Min temp " +  weatherList.main.temp_min.toString()
                binding.tvWind.text = weatherList.wind.deg.toString() + " degree"
                binding.tvWindSpeed.text = weatherList.wind.speed.toString() + " km/hour"
                binding.tvName.text = weatherList.sys.country
                binding.tvCountr.text = weatherList.name
                binding.tvSunrise.text =  unixTime(weatherList.sys.sunrise)
                binding.tvSunset.text =  unixTime(weatherList.sys.sunset)
                when(weatherList.weather[i].icon){
                    "01d"->binding.ivWeather.setImageResource(R.drawable.clear_sky)
                    "02d"->binding.ivWeather.setImageResource(R.drawable.few_clouds)
                    "03d"->binding.ivWeather.setImageResource(R.drawable.scattered_clouds)
                    "04d"->binding.ivWeather.setImageResource(R.drawable.broken_clouds)
                    "09d"->binding.ivWeather.setImageResource(R.drawable.shower_rain)
                    "10d"->binding.ivWeather.setImageResource(R.drawable.rain)
                    "11d"->binding.ivWeather.setImageResource(R.drawable.thunderstorm)
                    "13d"->binding.ivWeather.setImageResource(R.drawable.snow)
                    "50d"->binding.ivWeather.setImageResource(R.drawable.mist)
                }

            }
        }


    }
    private fun getUnit(value: String):String{
        var value = "C"
        if ("US"==value || "LR"==value||"MM"==value){
            value = "F"
        }
        return value
    }

    @SuppressLint("SimpleDateFormat")
    private fun unixTime(timex: Long): String? {
        val date = Date(timex * 1000)
        val sdf = SimpleDateFormat("HH:mm")
        return sdf.format(date)

    }
}