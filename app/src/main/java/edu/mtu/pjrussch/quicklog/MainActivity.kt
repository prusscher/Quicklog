package edu.mtu.pjrussch.quicklog

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.preference.PreferenceFragmentCompat
import edu.mtu.pjrussch.quicklog.API.APIController
import edu.mtu.pjrussch.quicklog.API.SDPAPI
import edu.mtu.pjrussch.quicklog.API.ServiceVolley
import kotlinx.android.synthetic.main.worklog_lookup.*
import kotlinx.android.synthetic.main.worklog_list_item.view.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import android.content.SharedPreferences
import android.hardware.input.InputManager
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.preference.PreferenceManager
import kotlinx.android.synthetic.main.worklog_list_item.*
import kotlinx.serialization.json.JSON


class MainActivity : AppCompatActivity() {

    val LOG_TAG = "please"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.worklog_lookup)


        // E6A03771-4A4E-4DE3-95F7-E9DB0D780C36

        title = "Quicklog"
        super.setTheme(R.style.DefaultDarkTheme)

        Log.d(LOG_TAG, "Populating scrollview")

        // How to add a viewz.
//        // Create an inflater for adding worklogs and such
        val layoutInflater:LayoutInflater = LayoutInflater.from(applicationContext)

        // Clear the scrollview
        if (worklog_scrollview.childCount > 0)
            worklog_scrollview.removeAllViews()

        // Get a reference to the default app prefs
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // Get a reference to our service and apiController
        val service = ServiceVolley(prefs.getString("sd_url", "https://servicedesk.mtu.edu/sdpapi/"))
        val apiController = APIController(service)

        Log.d(LOG_TAG, "Setting onClickListener for Button")

        search_worklog_button.setOnClickListener{

            val loadingView: View = layoutInflater.inflate(R.layout.worklog_loading_item, worklog_scrollview, false)
            loadingView.visibility = View.VISIBLE
            worklog_scrollview.addView(loadingView)

            val apiKey = prefs.getString("api_key", "NO_API_KEY")
            val path = requestWorklogAllPath(Integer.parseInt(request_input_field.text.toString()), apiKey)


            Log.d(LOG_TAG, "Button pressed, requesting worklogs from $path")

            if(apiKey != "NO_API_KEY") {
                apiController.post(path, JSONObject()) { response ->
                    queryAndSetWorklogsView(response)
                }
            } else {

                val worklogView: View = layoutInflater.inflate(R.layout.worklog_list_item, worklog_scrollview, false)

                worklogView.technican_field.text = "Status:"
                worklogView.date_field.text = SimpleDateFormat("MM/dd/yyyy hh:mm a").format(Date())
                worklogView.worklog_field.text = "No API Key set, go to settings and set the API Key from ServiceDesk."

                worklog_scrollview.addView(worklogView)
            }
        }

        // Assign the enter key to the same function as the go button
        request_input_field.setOnKeyListener { v, keycode, event ->
           if( event.action == KeyEvent.ACTION_DOWN && keycode == KeyEvent.KEYCODE_ENTER ) {

               val loadingView: View = layoutInflater.inflate(R.layout.worklog_loading_item, worklog_scrollview, false)
               loadingView.visibility = View.VISIBLE
               worklog_scrollview.addView(loadingView)

               val apiKey = prefs.getString("api_key", "NO_API_KEY")
               val path = requestWorklogAllPath(Integer.parseInt(request_input_field.text.toString()), apiKey)

               Log.d(LOG_TAG, "Button pressed, requesting worklogs from $path")

               if(apiKey != "NO_API_KEY") {
                   apiController.post(path, JSONObject()) { response ->
                       queryAndSetWorklogsView(response)
                   }
               } else {

                   val worklogView: View = layoutInflater.inflate(R.layout.worklog_list_item, worklog_scrollview, false)

                   worklogView.technican_field.text = "Status:"
                   worklogView.date_field.text = SimpleDateFormat("MM/dd/yyyy hh:mm a").format(Date())
                   worklogView.worklog_field.text = "No API Key set, go to settings and set the API Key from ServiceDesk."

                   worklog_scrollview.addView(worklogView)
               }



               true
           } else
               false
        }

        worklog_submit_button.setOnClickListener{
            submitWorklog(worklog_input_field.text.toString(), prefs, apiController)
        }

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
//        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        return when (item.itemId) {
            R.id.action_settings -> {
                val i = Intent(this, SettingsActivity::class.java)
                startActivity(i)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun submitWorklog(worklogString:String, prefs:SharedPreferences, apiController: APIController) {

        val apiKey = prefs.getString("api_key", "NO_API_KEY")

        // Lord is that dumb
        val inputData = JSONObject()
        val operationObj = inputData.put("operation", JSONObject()).getJSONObject("operation")
        val detailsObj = operationObj.put("details", JSONObject()).getJSONObject("details")
        val worklogsObj = detailsObj.put("worklogs", JSONObject()).getJSONObject("worklogs")
        val worklogObj = worklogsObj.put("worklog", JSONObject()).getJSONObject("worklog")
        worklogObj
            .put("description", "$worklogString")
            .put("workHours", "0")
            .put("workMinutes", "5")

        Log.d(LOG_TAG, "Generated worklog: ${inputData.toString()}")

        val path = requestAddWorklogPath(Integer.parseInt(request_input_field.text.toString()), apiKey, inputData)

        Log.d(LOG_TAG, "Button pressed, requesting worklogs from $path")

        if (apiKey != "NO_API_KEY") {
            apiController.post(path, JSONObject()) { response ->

                // Got response from the server, confirm that it was a success or failure and refresh
                // Clear the worklog input
                worklog_input_field.text.clear()


                // Refresh Path
                val path = requestWorklogAllPath(Integer.parseInt(request_input_field.text.toString()), apiKey)

                // Refresh list
                apiController.post(path, JSONObject()) { response ->
                    queryAndSetWorklogsView(response)
                }
            }
        }
    }

    fun hideKeyboard() {
        val inputManager:InputMethodManager = this.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.hideSoftInputFromWindow(this.currentFocus.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
    }

    fun queryAndSetWorklogsView(response: JSONObject?) {
        if (worklog_scrollview.childCount > 0)
            worklog_scrollview.removeAllViews()

        hideKeyboard()

        Log.d(LOG_TAG, "Got response:\n${response.toString()}")
        val obj =  response?.getJSONObject("operation")

        if(obj != null) {

            // Clear the list of worklogs
            if (worklog_scrollview.childCount > 0)
                worklog_scrollview.removeAllViews()

            // Grab the status of the result
            val status:String = obj.getJSONObject("result").getString("status")
            Log.d(LOG_TAG, "Status from servicedesk: $status")


            // Im lazy so this works for now
            try {
                val details:JSONArray? = obj.getJSONArray("Details")

                // Check if the request actually has worklogs
                if(details != null) {

                    val prefs = PreferenceManager.getDefaultSharedPreferences(this)

                    for(worklogIndex in if(!prefs.getBoolean("newestOrder", true)) (0..details.length()-1) else (details.length()-1 downTo 0 step 1)) {
                        Log.d(LOG_TAG, "Processing worklog $worklogIndex")

                        // Create a worklog view and get the object from the array of stuff
                        val worklogView: View = layoutInflater.inflate(R.layout.worklog_list_item, worklog_scrollview, false)
                        val worklogObj:JSONObject = details.getJSONObject(worklogIndex)

                        // Set the technician name
//                        Log.d(LOG_TAG, "technician: ${worklogObj.getString("technician")}")
                        worklogView.technican_field.text = worklogObj.getString("technician")

                        // Set the date (ex. 01/23/2019 12:34 PM)
                        val niceDate = SimpleDateFormat("MM/dd/yyyy hh:mm a").format(Date(worklogObj.getString("dateTime").toLong()))
//                        Log.d(LOG_TAG, "dateTime: $niceDate")
                        worklogView.date_field.text = niceDate

                        // Set the actual worklog
//                        Log.d(LOG_TAG, "worklog: ${worklogObj.getString("description")}")
                        worklogView.worklog_field.text = worklogObj.getString("description")

                        // Grab a reference to
                        worklog_scrollview.addView(worklogView)
                    }
                } else {
                    // Create a worklog view and get the object from the array of stuff
                    val worklogView: View = layoutInflater.inflate(R.layout.worklog_list_item, worklog_scrollview, false)

                    worklogView.technican_field.text = "Status:"
                    worklogView.date_field.text = SimpleDateFormat("MM/dd/yyyy hh:mm a").format(Date())
                    worklogView.worklog_field.text = "Status has no worklogs."

                    worklog_scrollview.addView(worklogView)
                }

            } catch ( e: Exception) {
                Toast.makeText(this, "Request failed with exception: ${e}", Toast.LENGTH_SHORT).show()
            }





        } else {
            if (worklog_scrollview.childCount > 0)
                worklog_scrollview.removeAllViews()

            // Create a worklog view and get the object from the array of stuff
            val worklogView: View = layoutInflater.inflate(R.layout.worklog_list_item, worklog_scrollview, false)

            worklogView.technican_field.text = "Status:"
            worklogView.date_field.text = SimpleDateFormat("MM/dd/yyyy hh:mm a").format(Date())
            worklogView.worklog_field.text = "Error while retrieving worklogs"

            worklog_scrollview.addView(worklogView)
        }
    }

    fun requestWorklogAllPath(requestID:Int, APIKEY:String): String {
        return "request/$requestID/worklogs/?OPERATION_NAME=${SDPAPI.GET_WORKLOGS}&TECHNICIAN_KEY=$APIKEY&format=json"
    }

    fun requestAddWorklogPath(requestID:Int, APIKEY:String, inputData:JSONObject): String {
        return "request/$requestID/worklogs/?OPERATION_NAME=${SDPAPI.ADD_WORKLOG}&TECHNICIAN_KEY=$APIKEY&format=json&INPUT_DATA=${inputData.toString()}"
    }

}

