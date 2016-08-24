/*
 * Geopaparazzi - Digital field mapping on Android based devices
 * Copyright (C) 2016  HydroloGIS (www.hydrologis.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package eu.geopaparazzi.library.webproject;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import eu.geopaparazzi.library.R;
import eu.geopaparazzi.library.database.GPLog;
import eu.geopaparazzi.library.util.GPDialogs;
import eu.geopaparazzi.library.util.TextRunnable;
import eu.geopaparazzi.library.util.TimeUtilities;

import static eu.geopaparazzi.library.util.LibraryConstants.PREFS_KEY_PWD;
import static eu.geopaparazzi.library.util.LibraryConstants.PREFS_KEY_URL;
import static eu.geopaparazzi.library.util.LibraryConstants.PREFS_KEY_USER;

/**
 * Web projects listing activity.
 *
 * @author Andrea Antonello (www.hydrologis.com)
 */
public class WebDataListActivity extends ListActivity {
    private static final String ERROR = "error"; //$NON-NLS-1$

    private ArrayAdapter<WebDataLayer> arrayAdapter;
    private EditText filterText;

    private List<WebDataLayer> projectList = new ArrayList<>();
    private List<WebDataLayer> dataListToLoad = new ArrayList<>();

    private String user;
    private String pwd;
    private String url;

    private ProgressDialog downloadDataListDialog;
    private ProgressDialog cloudProgressDialog;

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.webdatalist);

        Bundle extras = getIntent().getExtras();
        user = extras.getString(PREFS_KEY_USER);
        pwd = extras.getString(PREFS_KEY_PWD);
        url = extras.getString(PREFS_KEY_URL);

        filterText = (EditText) findViewById(R.id.search_box);
        filterText.addTextChangedListener(filterTextWatcher);

        downloadDataListDialog = ProgressDialog.show(this, getString(R.string.downloading),
                getString(R.string.downloading_layers_list_from_server));
        new AsyncTask<String, Void, String>() {

            protected String doInBackground(String... params) {
                WebDataListActivity context = WebDataListActivity.this;
                try {
                    projectList = WebDataManager.INSTANCE.downloadDataLayersList(context, url, user, pwd);
                    for (WebDataLayer wp : projectList) {
                        dataListToLoad.add(wp);
                    }
                    return ""; //$NON-NLS-1$
                } catch (Exception e) {
                    GPLog.error(this, null, e);
                    return ERROR;
                }
            }

            protected void onPostExecute(String response) { // on UI thread!
                GPDialogs.dismissProgressDialog(downloadDataListDialog);
                WebDataListActivity context = WebDataListActivity.this;
                if (response.equals(ERROR)) {
                    GPDialogs.warningDialog(context, getString(R.string.error_data_list), null);
                } else {
                    refreshList();
                }
            }

        }.execute((String) null);


        FloatingActionButton downloadButton = (FloatingActionButton) findViewById(R.id.downloadButton);
        downloadButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String defaultName = "spatialite_" + TimeUtilities.INSTANCE.TIMESTAMPFORMATTER_LOCAL.format(new Date()) + ".sqlite";

                GPDialogs.inputMessageDialog(WebDataListActivity.this, "Set name for downloaded database", defaultName, new TextRunnable() {
                    public ProgressDialog downloadDatabaseDialog;

                    @Override
                    public void run() {
                        StringBuilder sb = new StringBuilder();
                        for (WebDataLayer dataLayer : dataListToLoad) {
                            if (dataLayer.isSelected) {
                                sb.append(",").append("\"").append(dataLayer.name).append("\"");
                            }
                        }

                        String names = sb.substring(1);
                        String json = "{ layers: [ " + names + "] }";

                        downloadData(json);

                    }

                    private void downloadData(final String json) {
                        downloadDatabaseDialog = ProgressDialog.show(WebDataListActivity.this, getString(R.string.downloading),
                                getString(R.string.downloading_data_from_server));
                        new AsyncTask<String, Void, String>() {

                            protected String doInBackground(String... params) {
                                WebDataListActivity context = WebDataListActivity.this;
                                try {
                                    WebDataManager.INSTANCE.downloadData(WebDataListActivity.this, url, user, pwd, json, theTextToRunOn);
                                    return ""; //$NON-NLS-1$
                                } catch (Exception e) {
                                    GPLog.error(this, null, e);
                                    return ERROR + ":" + e.getLocalizedMessage();
                                }
                            }

                            protected void onPostExecute(String response) { // on UI thread!
                                GPDialogs.dismissProgressDialog(downloadDatabaseDialog);
                                WebDataListActivity context = WebDataListActivity.this;
                                String okMsg = getString(R.string.data_successfully_downloaded);
                                if (response.startsWith(ERROR)) {
                                    GPDialogs.warningDialog(WebDataListActivity.this, response, null);
                                } else {
                                    GPDialogs.infoDialog(WebDataListActivity.this, okMsg, null);
                                }
                            }

                        }.execute((String) null);

                    }


                });
            }
        });

    }


    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    @Override
    protected void onPause() {
        GPDialogs.dismissProgressDialog(downloadDataListDialog);
        GPDialogs.dismissProgressDialog(cloudProgressDialog);
        super.onPause();
    }

    protected void onDestroy() {
        super.onDestroy();
        filterText.removeTextChangedListener(filterTextWatcher);
    }

    private void filterList(String filterText) {
        if (GPLog.LOG)
            GPLog.addLogEntry(this, "filter projects list"); //$NON-NLS-1$

        dataListToLoad.clear();
        for (WebDataLayer webDataLayer : projectList) {
            if (webDataLayer.matches(filterText)) {
                dataListToLoad.add(webDataLayer);
            }
        }

        refreshList();
    }

    private void refreshList() {
        if (GPLog.LOG)
            GPLog.addLogEntry(this, "refreshing projects list"); //$NON-NLS-1$
        arrayAdapter = new ArrayAdapter<WebDataLayer>(this, R.layout.webdatarow, dataListToLoad) {
            @Override
            public View getView(int position, View cView, ViewGroup parent) {
                LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                final View rowView = inflater.inflate(R.layout.webdatarow, null);

                final WebDataLayer webDataLayer = dataListToLoad.get(position);
                TextView titleText = (TextView) rowView.findViewById(R.id.titletext);
                TextView descriptionText = (TextView) rowView.findViewById(R.id.descriptiontext);
                TextView geomTypeText = (TextView) rowView.findViewById(R.id.geomtypetext);
                TextView sridText = (TextView) rowView.findViewById(R.id.sridtext);
                final CheckBox selectedBox = (CheckBox) rowView.findViewById(R.id.selectedCheck);
                selectedBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        webDataLayer.isSelected = selectedBox.isChecked();
                    }
                });

                titleText.setText(webDataLayer.name);
                descriptionText.setText(webDataLayer.title);
                geomTypeText.setText(webDataLayer.geomtype);
                sridText.setText(webDataLayer.srid);
                return rowView;
            }
        };

        setListAdapter(arrayAdapter);
    }

    private TextWatcher filterTextWatcher = new TextWatcher() {

        public void afterTextChanged(Editable s) {
            // ignore
        }

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // ignore
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // arrayAdapter.getFilter().filter(s);
            filterList(s.toString());
        }
    };

}
