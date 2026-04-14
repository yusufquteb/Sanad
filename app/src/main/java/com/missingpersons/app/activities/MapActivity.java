package com.missingpersons.app.activities;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.*;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.*;
import com.missingpersons.app.R;
import com.missingpersons.app.utils.ProximityAlertManager;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MapActivity — خريطة المفقودين (OSMDroid)
 *
 * إصلاح GPS:
 * - يفحص إن كان GPS provider مفعّل فعلاً
 * - لو GPS متوقف يعرض dialog للمستخدم يفتح الإعدادات
 * - يستخدم PRIORITY_HIGH_ACCURACY مع setWaitForAccurateLocation(true)
 *
 * إصلاح BottomSheet:
 * - استبدال BottomSheetDialog بـ BottomSheetBehavior مدمج في اللاي آوت
 * - يضمن أن البطاقة لا تطغى على الـ AppBar
 */
public class MapActivity extends AppCompatActivity {

    public static final String EXTRA_PICK_MODE = "PICK_MODE";
    public static final String EXTRA_LAT       = "lat";
    public static final String EXTRA_LNG       = "lng";
    public static final String EXTRA_ADDRESS   = "address";
    private static final int   LOCATION_PERM   = 200;

    private MapView            mapView;
    private IMapController     mapController;
    private ChipGroup          chipFilter;
    private FloatingActionButton fabMyLocation;
    private com.google.android.material.floatingactionbutton.FloatingActionButton fabGovFilter;
    private com.google.android.material.floatingactionbutton.FloatingActionButton fabHeatmap;
    private boolean heatmapEnabled = false;
    private final List<org.osmdroid.views.overlay.Polygon> heatmapCircles = new ArrayList<>();
    private TextView           tvNearbyAlert, tvPickInstruction;
    private MaterialButton     btnConfirmPin;
    private MyLocationNewOverlay myLocationOverlay;
    private FusedLocationProviderClient fusedClient;
    private LocationCallback   locationCallback;

    // ── Bottom Sheet مدمج (بدلاً من BottomSheetDialog) ──────────────────
    private LinearLayout                 caseBottomSheet;
    private BottomSheetBehavior<LinearLayout> caseSheetBehavior;
    // ─────────────────────────────────────────────────────────────────────

    private boolean  pickMode = false;
    private Marker   selectedMarker;
    private double   selectedLat, selectedLng;
    private String   selectedAddress = "";
    private String   currentFilter   = "all";
    private String   governorateFilter = "all";   // فلتر المحافظة
    private Location lastUserLocation;
    private final List<Marker> caseMarkers = new ArrayList<>();

    @Override
    protected void attachBaseContext(Context c) {
        super.attachBaseContext(com.missingpersons.app.utils.LanguageHelper.applyLanguage(c));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_map);

        // ── إخفاء شريط التنقل السفلي للنظام لتجنب تغطية عناصر الخريطة ──
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController ctrl = getWindow().getInsetsController();
            if (ctrl != null) {
                ctrl.hide(android.view.WindowInsets.Type.navigationBars());
                ctrl.setSystemBarsBehavior(
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            //noinspection deprecation
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }

        pickMode = getIntent().getBooleanExtra(EXTRA_PICK_MODE, false);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(pickMode ? "📍 اختر الموقع" : "🗺️ خريطة المفقودين");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        initMap();
        initViews();
        initCaseBottomSheet();   // ← تهيئة الـ sheet المدمج
        checkLocationPermission();
    }

    // ════════════════════════════════════════════════════════════════════
    //  Bottom Sheet مدمج — لا يطغى على الـ AppBar
    // ════════════════════════════════════════════════════════════════════

    private void initCaseBottomSheet() {
        caseBottomSheet    = findViewById(R.id.bottom_sheet_case);
        caseSheetBehavior  = BottomSheetBehavior.from(caseBottomSheet);
        caseSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        caseSheetBehavior.setHideable(true);
        caseSheetBehavior.setPeekHeight(0);

        // إغلاق الـ sheet عند الضغط على الخريطة
        if (!pickMode) {
            MapEventsReceiver tapReceiver = new MapEventsReceiver() {
                @Override public boolean singleTapConfirmedHelper(GeoPoint p) {
                    if (caseSheetBehavior.getState() != BottomSheetBehavior.STATE_HIDDEN) {
                        caseSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                        return true;
                    }
                    return false;
                }
                @Override public boolean longPressHelper(GeoPoint p) { return false; }
            };
            mapView.getOverlays().add(new MapEventsOverlay(tapReceiver));
        }
    }

    private void showCaseBottomSheet(DataSnapshot data) {
        TextView       tvName    = caseBottomSheet.findViewById(R.id.bs_tv_name);
        TextView       tvAddr    = caseBottomSheet.findViewById(R.id.bs_tv_addr);
        MaterialButton btnDetails = caseBottomSheet.findViewById(R.id.bs_btn_details);
        MaterialButton btnNav    = caseBottomSheet.findViewById(R.id.bs_btn_navigate);
        MaterialButton btnWA     = caseBottomSheet.findViewById(R.id.bs_btn_whatsapp);

        String name = data.child("personName").getValue(String.class);
        String addr = data.child("manualAddress").getValue(String.class);
        Double lat  = data.child("lat").getValue(Double.class);
        Double lng  = data.child("lng").getValue(Double.class);
        String id   = data.getKey();

        if (tvName != null) tvName.setText(name != null ? name : "مجهول");
        if (tvAddr != null) tvAddr.setText(addr != null ? addr : "");

        if (btnDetails != null) btnDetails.setOnClickListener(b -> {
            if (id != null) {
                startActivity(new Intent(this, CaseDetailActivity.class)
                    .putExtra("reportId", id));
            }
            caseSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        });

        if (btnWA != null) btnWA.setOnClickListener(b -> {
            String text = "شاهدت بلاغاً عن مفقود: " + (name != null ? name : "مجهول");
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://wa.me/?text=" + Uri.encode(text))));
            } catch (Exception ignored) {}
        });

        if (btnNav != null && lat != null && lng != null) {
            btnNav.setOnClickListener(b -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("geo:" + lat + "," + lng + "?q=" + lat + "," + lng)));
                } catch (Exception ignored) {}
                caseSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            });
        }

        caseSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    // ════════════════════════════════════════════════════════════════════
    //  Map Init
    // ════════════════════════════════════════════════════════════════════

    private void initMap() {
        mapView = findViewById(R.id.osm_map_view);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapController = mapView.getController();
        mapController.setZoom(6.0);
        mapController.setCenter(new GeoPoint(26.8206, 30.8025));

        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), mapView);
        mapView.getOverlays().add(myLocationOverlay);

        if (pickMode) {
            MapEventsReceiver receiver = new MapEventsReceiver() {
                @Override public boolean singleTapConfirmedHelper(GeoPoint p) { onMapTapped(p); return true; }
                @Override public boolean longPressHelper(GeoPoint p) { onMapTapped(p); return true; }
            };
            mapView.getOverlays().add(new MapEventsOverlay(receiver));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Location
    // ════════════════════════════════════════════════════════════════════

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            checkGpsProviderAndEnable();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("📍 إذن الموقع مطلوب")
            .setMessage("يحتاج التطبيق لموقعك لعرض الحالات القريبة منك وتحديد مكانك على الخريطة.")
            .setPositiveButton("السماح", (d, w) ->
                ActivityCompat.requestPermissions(this,
                    new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    }, LOCATION_PERM))
            .setNegativeButton("لاحقاً", (d, w) -> {
                if (!pickMode) loadCasesOnMap();
            })
            .setCancelable(false).show();
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == LOCATION_PERM) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                checkGpsProviderAndEnable();
            } else {
                Toast.makeText(this, "يمكنك تفعيل الموقع لاحقاً من الإعدادات", Toast.LENGTH_LONG).show();
                if (!pickMode) loadCasesOnMap();
            }
        }
    }

    private void checkGpsProviderAndEnable() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean gpsEnabled = lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER);

        if (!gpsEnabled) {
            new AlertDialog.Builder(this)
                .setTitle("📡 GPS غير مفعّل")
                .setMessage("يحتاج التطبيق لتفعيل GPS للحصول على موقعك بدقة.\n\nاذهب لـ الإعدادات > الموقع وفعّل GPS.")
                .setPositiveButton("فتح الإعدادات", (d, w) -> {
                    startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                })
                .setNegativeButton("تخطي", (d, w) -> enableMyLocation())
                .setCancelable(false).show();
        } else {
            enableMyLocation();
        }
    }

    private void enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        myLocationOverlay.enableMyLocation();

        fusedClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null && location.getAccuracy() < 150) {
                lastUserLocation = location;
                GeoPoint pos = new GeoPoint(location.getLatitude(), location.getLongitude());
                mapController.animateTo(pos, 14.0, 800L);
                if (!pickMode) checkProximityAlerts(location);
            }
            requestFreshLocation();
        }).addOnFailureListener(e -> requestFreshLocation());

        if (!pickMode) loadCasesOnMap();
    }

    private void requestFreshLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
            .setMinUpdateIntervalMillis(1500)
            .setMaxUpdates(5)
            .setWaitForAccurateLocation(true)
            .setMinUpdateDistanceMeters(5f)
            .build();

        locationCallback = new LocationCallback() {
            @Override public void onLocationResult(@NonNull LocationResult r) {
                Location loc = r.getLastLocation();
                if (loc == null) return;
                lastUserLocation = loc;
                GeoPoint pos = new GeoPoint(loc.getLatitude(), loc.getLongitude());
                if (loc.getAccuracy() < 50) {
                    mapController.animateTo(pos, 15.0, 600L);
                } else {
                    mapController.animateTo(pos, 13.0, 600L);
                }
            }
        };
        fusedClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper());
    }

    // ════════════════════════════════════════════════════════════════════
    //  Views
    // ════════════════════════════════════════════════════════════════════

    private void initViews() {
        chipFilter        = findViewById(R.id.chip_filter_group);
        fabMyLocation     = findViewById(R.id.fab_my_location);
        fabGovFilter      = findViewById(R.id.fab_gov_filter);
        tvNearbyAlert     = findViewById(R.id.tv_nearby_alert);
        btnConfirmPin     = findViewById(R.id.btn_confirm_pin);
        tvPickInstruction = findViewById(R.id.tv_pick_instruction);

        if (fabMyLocation != null) {
            fabMyLocation.setOnClickListener(v -> {
                if (lastUserLocation != null) {
                    GeoPoint pos = new GeoPoint(
                        lastUserLocation.getLatitude(), lastUserLocation.getLongitude());
                    mapController.animateTo(pos, 15.0, 500L);
                } else {
                    Toast.makeText(this, "⏳ جارٍ تحديد موقعك...", Toast.LENGTH_SHORT).show();
                    checkGpsProviderAndEnable();
                }
            });
        }

        // ── فلتر المحافظة ──────────────────────────────────────────
        if (fabGovFilter != null) {
            fabGovFilter.setOnClickListener(v -> showGovernorateFilterDialog());
        }

        fabHeatmap = findViewById(R.id.fab_heatmap);
        if (fabHeatmap != null) {
            fabHeatmap.setOnClickListener(v -> {
                heatmapEnabled = !heatmapEnabled;
                fabHeatmap.setImageResource(heatmapEnabled
                    ? android.R.drawable.ic_menu_close_clear_cancel
                    : android.R.drawable.ic_dialog_map);
                if (heatmapEnabled) showHeatmap();
                else clearHeatmap();
            });
        }

        if (pickMode) {
            if (chipFilter != null)        chipFilter.setVisibility(View.GONE);
            if (tvNearbyAlert != null)     tvNearbyAlert.setVisibility(View.GONE);
            if (tvPickInstruction != null) {
                tvPickInstruction.setVisibility(View.VISIBLE);
                tvPickInstruction.setText("📍 اضغط على الخريطة لتحديد الموقع");
            }
            if (btnConfirmPin != null) {
                btnConfirmPin.setVisibility(View.GONE);
                btnConfirmPin.setOnClickListener(v -> returnSelectedLocation());
            }
        } else {
            if (btnConfirmPin != null)     btnConfirmPin.setVisibility(View.GONE);
            if (tvPickInstruction != null) tvPickInstruction.setVisibility(View.GONE);
            if (chipFilter != null) {
                chipFilter.setOnCheckedChangeListener((g, id) -> {
                    if      (id == R.id.chip_all)    currentFilter = "all";
                    else if (id == R.id.chip_male)   currentFilter = "ذكر";
                    else if (id == R.id.chip_female) currentFilter = "أنثى";
                    else if (id == R.id.chip_missing) currentFilter = "missing";
                    else if (id == R.id.chip_found)   currentFilter = "found";
                    loadCasesOnMap();
                });
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Pick Mode
    // ════════════════════════════════════════════════════════════════════

    private void onMapTapped(GeoPoint p) {
        selectedLat = p.getLatitude();
        selectedLng = p.getLongitude();

        if (selectedMarker != null) mapView.getOverlays().remove(selectedMarker);
        selectedMarker = new Marker(mapView);
        selectedMarker.setPosition(p);
        selectedMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        selectedMarker.setTitle("📍 الموقع المحدد");
        mapView.getOverlays().add(selectedMarker);
        mapView.invalidate();

        new Thread(() -> {
            try {
                Geocoder geo = new Geocoder(this, new Locale("ar"));
                List<android.location.Address> addrs =
                    geo.getFromLocation(selectedLat, selectedLng, 1);
                if (addrs != null && !addrs.isEmpty()) {
                    android.location.Address a = addrs.get(0);
                    selectedAddress = a.getAddressLine(0) != null ? a.getAddressLine(0) : "";
                }
            } catch (Exception ignored) {}
            runOnUiThread(() -> {
                if (btnConfirmPin != null) btnConfirmPin.setVisibility(View.VISIBLE);
            });
        }).start();
    }

    private void returnSelectedLocation() {
        Intent result = new Intent();
        result.putExtra(EXTRA_LAT, selectedLat);
        result.putExtra(EXTRA_LNG, selectedLng);
        result.putExtra(EXTRA_ADDRESS, selectedAddress);
        setResult(RESULT_OK, result);
        finish();
    }

    // ════════════════════════════════════════════════════════════════════
    //  Firebase — Load Cases
    // ════════════════════════════════════════════════════════════════════

    // ── فلتر المحافظات ───────────────────────────────────────────────────
    private static final String[] EGYPT_GOVERNORATES = {
        "all", "القاهرة", "الجيزة", "الإسكندرية", "الدقهلية", "الشرقية",
        "القليوبية", "البحيرة", "كفر الشيخ", "الغربية", "المنوفية",
        "الفيوم", "بني سويف", "المنيا", "أسيوط", "سوهاج", "قنا",
        "الأقصر", "أسوان", "البحر الأحمر", "الوادي الجديد", "مطروح",
        "شمال سيناء", "جنوب سيناء", "السويس", "الإسماعيلية", "بورسعيد"
    };

    private void showGovernorateFilterDialog() {
        String[] labels = new String[EGYPT_GOVERNORATES.length];
        labels[0] = "🗺️ كل المحافظات";
        for (int i = 1; i < EGYPT_GOVERNORATES.length; i++)
            labels[i] = EGYPT_GOVERNORATES[i];

        int currentIdx = 0;
        for (int i = 0; i < EGYPT_GOVERNORATES.length; i++)
            if (EGYPT_GOVERNORATES[i].equals(governorateFilter)) { currentIdx = i; break; }

        new AlertDialog.Builder(this)
            .setTitle("📍 اختر المحافظة")
            .setSingleChoiceItems(labels, currentIdx, (dialog, which) -> {
                governorateFilter = EGYPT_GOVERNORATES[which];
                dialog.dismiss();
                loadCasesOnMap();
                // تحديث أيقونة الـ FAB
                if (fabGovFilter != null) {
                    fabGovFilter.setImageResource(
                        "all".equals(governorateFilter)
                            ? android.R.drawable.ic_menu_mapmode
                            : android.R.drawable.ic_menu_myplaces);
                }
            })
            .setNegativeButton("إلغاء", null)
            .show();
    }

        private void loadCasesOnMap() {
        for (Marker m : caseMarkers) mapView.getOverlays().remove(m);
        caseMarkers.clear();

        // نجلب كل البلاغات ونفلتر محلياً لتجنب مشاكل index مركبة في Firebase
        com.google.firebase.database.FirebaseDatabase
            .getInstance().getReference("reports")
            .addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                for (DataSnapshot c : snap.getChildren()) {
                    // ── تحقق من الموافقة — يدعم Boolean وString ──
                    Object approvedRaw = c.child("approved").getValue();
                    boolean isApproved = Boolean.TRUE.equals(approvedRaw)
                        || "true".equalsIgnoreCase(String.valueOf(approvedRaw));
                    if (!isApproved) continue;

                    Double lat = c.child("lat").getValue(Double.class);
                    Double lng = c.child("lng").getValue(Double.class);
                    if (lat == null || lng == null || (lat == 0 && lng == 0)) continue;

                    String gender = c.child("personGender").getValue(String.class);
                    String type   = c.child("reportType").getValue(String.class);

                    // ── فلتر الحالة / الجنس ──
                    if (!"all".equals(currentFilter)) {
                        if ("missing".equals(currentFilter) || "found".equals(currentFilter)) {
                            if (!currentFilter.equals(type)) continue;
                        } else {
                            if (!currentFilter.equals(gender)) continue;
                        }
                    }

                    // ── فلتر المحافظة ─────────────────────────────
                    if (!"all".equals(governorateFilter)) {
                        String gov = c.child("governorate").getValue(String.class);
                        if (gov == null || gov.trim().isEmpty()) continue;
                        if (!gov.trim().equals(governorateFilter.trim())) continue;
                    }

                    String name = c.child("personName").getValue(String.class);
                    String addr = c.child("manualAddress").getValue(String.class);

                    Marker marker = new Marker(mapView);
                    marker.setPosition(new GeoPoint(lat, lng));
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                    marker.setTitle(name != null ? name : "مجهول");
                    marker.setSnippet(addr != null ? addr : "");
                    final DataSnapshot caseData = c;
                    marker.setOnMarkerClickListener((m, mv) -> {
                        showCaseBottomSheet(caseData);
                        return true;
                    });
                    caseMarkers.add(marker);
                    mapView.getOverlays().add(marker);
                }
                mapView.invalidate();

                // إظهار نتيجة الفلتر
                if (tvNearbyAlert != null && !"all".equals(governorateFilter)) {
                    tvNearbyAlert.setVisibility(View.VISIBLE);
                    tvNearbyAlert.setText(caseMarkers.isEmpty()
                        ? "📍 لا توجد حالات في " + governorateFilter
                        : "📍 " + governorateFilter + " — " + caseMarkers.size() + " حالة");
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                Toast.makeText(MapActivity.this,
                    "⚠️ تعذّر تحميل البيانات", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkProximityAlerts(Location loc) {
        ProximityAlertManager.checkProximity(this, loc.getLatitude(), loc.getLongitude(),
            (report, distanceKm) -> runOnUiThread(() -> {
                if (tvNearbyAlert != null) {
                    tvNearbyAlert.setVisibility(View.VISIBLE);
                    tvNearbyAlert.setText("⚠️ مفقود قريب: "
                        + (report.getPersonName() != null ? report.getPersonName() : "مجهول")
                        + " (" + ProximityAlertManager.formatDistance(distanceKm) + ")");
                }
            }));
    }

    // ════════════════════════════════════════════════════════════════════
    //  Lifecycle
    // ════════════════════════════════════════════════════════════════════

    // ════════════════════════════════════════════════════════════════════
    //  Heatmap — خريطة حرارية بالدوائر المتراكبة
    // ════════════════════════════════════════════════════════════════════

    /**
     * يجمع البلاغات حسب المحافظة ويرسم دائرة لكل منطقة —
     * كلما كبرت الدائرة وكانت أكثر حمرةً كلما زاد عدد البلاغات.
     */
    private void showHeatmap() {
        clearHeatmap();
        FirebaseDatabase.getInstance().getReference("reports")
            .orderByChild("approved").equalTo(true)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    // تجميع: { "governorate" → {count, sumLat, sumLng} }
                    java.util.HashMap<String, int[]>    countMap = new java.util.HashMap<>();
                    java.util.HashMap<String, double[]> coordMap = new java.util.HashMap<>();

                    for (DataSnapshot c : snap.getChildren()) {
                        String gov = c.child("governorate").getValue(String.class);
                        Double lat = c.child("lat").getValue(Double.class);
                        Double lng = c.child("lng").getValue(Double.class);
                        if (gov == null || gov.isEmpty()) continue;

                        countMap.merge(gov, new int[]{1}, (a, b) -> new int[]{a[0] + 1});
                        if (lat != null && lng != null && !(lat == 0 && lng == 0)) {
                            coordMap.merge(gov,
                                new double[]{lat, lng, 1},
                                (a, b) -> new double[]{a[0] + lat, a[1] + lng, a[2] + 1});
                        }
                    }

                    // أكثر محافظة لتحديد النسبة
                    int maxCount = 1;
                    for (int[] v : countMap.values())
                        if (v[0] > maxCount) maxCount = v[0];

                    for (String gov : countMap.keySet()) {
                        int count = countMap.get(gov)[0];
                        double[] coords = coordMap.get(gov);
                        if (coords == null) continue;

                        double lat = coords[0] / coords[2];
                        double lng = coords[1] / coords[2];
                        float ratio = (float) count / maxCount;

                        // لون من أصفر إلى أحمر حسب الكثافة
                        int red   = 255;
                        int green = (int)(255 * (1 - ratio));
                        int alpha = (int)(120 + 80 * ratio); // شفافية متغيرة
                        int color = android.graphics.Color.argb(alpha, red, green, 0);

                        // نصف قطر يتناسب مع عدد البلاغات (500m → 5000m)
                        double radiusMeters = 500 + (4500 * ratio);

                        org.osmdroid.views.overlay.Polygon circle =
                            new org.osmdroid.views.overlay.Polygon();
                        circle.setPoints(org.osmdroid.views.overlay.Polygon
                            .pointsAsCircle(new GeoPoint(lat, lng), radiusMeters));
                        circle.getFillPaint().setColor(color);
                        circle.getOutlinePaint().setColor(
                            android.graphics.Color.argb(200, red, green, 0));
                        circle.getOutlinePaint().setStrokeWidth(2f);
                        circle.setTitle(gov + " — " + count + " بلاغ");
                        circle.setOnClickListener((polygon, mv, pt) -> {
                            Toast.makeText(MapActivity.this,
                                gov + ": " + count + " بلاغ", Toast.LENGTH_SHORT).show();
                            return true;
                        });

                        heatmapCircles.add(circle);
                        mapView.getOverlays().add(circle);
                    }
                    mapView.invalidate();
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
    }

    private void clearHeatmap() {
        for (org.osmdroid.views.overlay.Polygon p : heatmapCircles)
            mapView.getOverlays().remove(p);
        heatmapCircles.clear();
        mapView.invalidate();
    }

    @Override protected void onResume() { super.onResume(); mapView.onResume(); }
    @Override protected void onPause()  {
        super.onPause();
        mapView.onPause();
        if (fusedClient != null && locationCallback != null)
            fusedClient.removeLocationUpdates(locationCallback);
    }
    @Override public boolean onSupportNavigateUp() { onBackPressed(); return true; }
}
