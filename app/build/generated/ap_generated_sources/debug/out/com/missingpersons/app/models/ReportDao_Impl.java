package com.missingpersons.app.models;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ReportDao_Impl implements ReportDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ReportEntity> __insertionAdapterOfReportEntity;

  private final SharedSQLiteStatement __preparedStmtOfMarkSynced;

  private final SharedSQLiteStatement __preparedStmtOfUpdateStatus;

  private final SharedSQLiteStatement __preparedStmtOfMarkApproved;

  private final SharedSQLiteStatement __preparedStmtOfDelete;

  private final SharedSQLiteStatement __preparedStmtOfCleanOld;

  public ReportDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfReportEntity = new EntityInsertionAdapter<ReportEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `reports` (`reportId`,`personName`,`description`,`personAge`,`personGender`,`governorate`,`manualAddress`,`lat`,`lng`,`imageUrl`,`reportType`,`status`,`approved`,`reporterId`,`timestamp`,`faceEmbedding`,`synced`,`lastUpdated`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final ReportEntity entity) {
        if (entity.reportId == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.reportId);
        }
        if (entity.personName == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.personName);
        }
        if (entity.description == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.description);
        }
        if (entity.personAge == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.personAge);
        }
        if (entity.personGender == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.personGender);
        }
        if (entity.governorate == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.governorate);
        }
        if (entity.manualAddress == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.manualAddress);
        }
        statement.bindDouble(8, entity.lat);
        statement.bindDouble(9, entity.lng);
        if (entity.imageUrl == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, entity.imageUrl);
        }
        if (entity.reportType == null) {
          statement.bindNull(11);
        } else {
          statement.bindString(11, entity.reportType);
        }
        if (entity.status == null) {
          statement.bindNull(12);
        } else {
          statement.bindString(12, entity.status);
        }
        final int _tmp = entity.approved ? 1 : 0;
        statement.bindLong(13, _tmp);
        if (entity.reporterId == null) {
          statement.bindNull(14);
        } else {
          statement.bindString(14, entity.reporterId);
        }
        statement.bindLong(15, entity.timestamp);
        if (entity.faceEmbedding == null) {
          statement.bindNull(16);
        } else {
          statement.bindString(16, entity.faceEmbedding);
        }
        final int _tmp_1 = entity.synced ? 1 : 0;
        statement.bindLong(17, _tmp_1);
        statement.bindLong(18, entity.lastUpdated);
      }
    };
    this.__preparedStmtOfMarkSynced = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE reports SET synced = 1 WHERE reportId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateStatus = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE reports SET status = ?, lastUpdated = ? WHERE reportId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfMarkApproved = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE reports SET approved = 1, status = 'approved', lastUpdated = ? WHERE reportId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDelete = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM reports WHERE reportId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfCleanOld = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM reports WHERE approved = 0 AND synced = 1 AND timestamp < ?";
        return _query;
      }
    };
  }

  @Override
  public void insertOrUpdate(final ReportEntity report) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfReportEntity.insert(report);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void insertAll(final List<ReportEntity> reports) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfReportEntity.insert(reports);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void markSynced(final String id) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfMarkSynced.acquire();
    int _argIndex = 1;
    if (id == null) {
      _stmt.bindNull(_argIndex);
    } else {
      _stmt.bindString(_argIndex, id);
    }
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfMarkSynced.release(_stmt);
    }
  }

  @Override
  public void updateStatus(final String id, final String status, final long ts) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateStatus.acquire();
    int _argIndex = 1;
    if (status == null) {
      _stmt.bindNull(_argIndex);
    } else {
      _stmt.bindString(_argIndex, status);
    }
    _argIndex = 2;
    _stmt.bindLong(_argIndex, ts);
    _argIndex = 3;
    if (id == null) {
      _stmt.bindNull(_argIndex);
    } else {
      _stmt.bindString(_argIndex, id);
    }
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfUpdateStatus.release(_stmt);
    }
  }

  @Override
  public void markApproved(final String id, final long ts) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfMarkApproved.acquire();
    int _argIndex = 1;
    _stmt.bindLong(_argIndex, ts);
    _argIndex = 2;
    if (id == null) {
      _stmt.bindNull(_argIndex);
    } else {
      _stmt.bindString(_argIndex, id);
    }
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfMarkApproved.release(_stmt);
    }
  }

  @Override
  public void delete(final String id) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfDelete.acquire();
    int _argIndex = 1;
    if (id == null) {
      _stmt.bindNull(_argIndex);
    } else {
      _stmt.bindString(_argIndex, id);
    }
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfDelete.release(_stmt);
    }
  }

  @Override
  public void cleanOld(final long cutoff) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfCleanOld.acquire();
    int _argIndex = 1;
    _stmt.bindLong(_argIndex, cutoff);
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfCleanOld.release(_stmt);
    }
  }

  @Override
  public LiveData<List<ReportEntity>> getApprovedReports() {
    final String _sql = "SELECT * FROM reports WHERE approved = 1 ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return __db.getInvalidationTracker().createLiveData(new String[] {"reports"}, false, new Callable<List<ReportEntity>>() {
      @Override
      @Nullable
      public List<ReportEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfReportId = CursorUtil.getColumnIndexOrThrow(_cursor, "reportId");
          final int _cursorIndexOfPersonName = CursorUtil.getColumnIndexOrThrow(_cursor, "personName");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfPersonAge = CursorUtil.getColumnIndexOrThrow(_cursor, "personAge");
          final int _cursorIndexOfPersonGender = CursorUtil.getColumnIndexOrThrow(_cursor, "personGender");
          final int _cursorIndexOfGovernorate = CursorUtil.getColumnIndexOrThrow(_cursor, "governorate");
          final int _cursorIndexOfManualAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "manualAddress");
          final int _cursorIndexOfLat = CursorUtil.getColumnIndexOrThrow(_cursor, "lat");
          final int _cursorIndexOfLng = CursorUtil.getColumnIndexOrThrow(_cursor, "lng");
          final int _cursorIndexOfImageUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "imageUrl");
          final int _cursorIndexOfReportType = CursorUtil.getColumnIndexOrThrow(_cursor, "reportType");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfApproved = CursorUtil.getColumnIndexOrThrow(_cursor, "approved");
          final int _cursorIndexOfReporterId = CursorUtil.getColumnIndexOrThrow(_cursor, "reporterId");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfFaceEmbedding = CursorUtil.getColumnIndexOrThrow(_cursor, "faceEmbedding");
          final int _cursorIndexOfSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "synced");
          final int _cursorIndexOfLastUpdated = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUpdated");
          final List<ReportEntity> _result = new ArrayList<ReportEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ReportEntity _item;
            _item = new ReportEntity();
            if (_cursor.isNull(_cursorIndexOfReportId)) {
              _item.reportId = null;
            } else {
              _item.reportId = _cursor.getString(_cursorIndexOfReportId);
            }
            if (_cursor.isNull(_cursorIndexOfPersonName)) {
              _item.personName = null;
            } else {
              _item.personName = _cursor.getString(_cursorIndexOfPersonName);
            }
            if (_cursor.isNull(_cursorIndexOfDescription)) {
              _item.description = null;
            } else {
              _item.description = _cursor.getString(_cursorIndexOfDescription);
            }
            if (_cursor.isNull(_cursorIndexOfPersonAge)) {
              _item.personAge = null;
            } else {
              _item.personAge = _cursor.getString(_cursorIndexOfPersonAge);
            }
            if (_cursor.isNull(_cursorIndexOfPersonGender)) {
              _item.personGender = null;
            } else {
              _item.personGender = _cursor.getString(_cursorIndexOfPersonGender);
            }
            if (_cursor.isNull(_cursorIndexOfGovernorate)) {
              _item.governorate = null;
            } else {
              _item.governorate = _cursor.getString(_cursorIndexOfGovernorate);
            }
            if (_cursor.isNull(_cursorIndexOfManualAddress)) {
              _item.manualAddress = null;
            } else {
              _item.manualAddress = _cursor.getString(_cursorIndexOfManualAddress);
            }
            _item.lat = _cursor.getDouble(_cursorIndexOfLat);
            _item.lng = _cursor.getDouble(_cursorIndexOfLng);
            if (_cursor.isNull(_cursorIndexOfImageUrl)) {
              _item.imageUrl = null;
            } else {
              _item.imageUrl = _cursor.getString(_cursorIndexOfImageUrl);
            }
            if (_cursor.isNull(_cursorIndexOfReportType)) {
              _item.reportType = null;
            } else {
              _item.reportType = _cursor.getString(_cursorIndexOfReportType);
            }
            if (_cursor.isNull(_cursorIndexOfStatus)) {
              _item.status = null;
            } else {
              _item.status = _cursor.getString(_cursorIndexOfStatus);
            }
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfApproved);
            _item.approved = _tmp != 0;
            if (_cursor.isNull(_cursorIndexOfReporterId)) {
              _item.reporterId = null;
            } else {
              _item.reporterId = _cursor.getString(_cursorIndexOfReporterId);
            }
            _item.timestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            if (_cursor.isNull(_cursorIndexOfFaceEmbedding)) {
              _item.faceEmbedding = null;
            } else {
              _item.faceEmbedding = _cursor.getString(_cursorIndexOfFaceEmbedding);
            }
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfSynced);
            _item.synced = _tmp_1 != 0;
            _item.lastUpdated = _cursor.getLong(_cursorIndexOfLastUpdated);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public List<ReportEntity> getApprovedReportsSync() {
    final String _sql = "SELECT * FROM reports WHERE approved = 1 ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfReportId = CursorUtil.getColumnIndexOrThrow(_cursor, "reportId");
      final int _cursorIndexOfPersonName = CursorUtil.getColumnIndexOrThrow(_cursor, "personName");
      final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
      final int _cursorIndexOfPersonAge = CursorUtil.getColumnIndexOrThrow(_cursor, "personAge");
      final int _cursorIndexOfPersonGender = CursorUtil.getColumnIndexOrThrow(_cursor, "personGender");
      final int _cursorIndexOfGovernorate = CursorUtil.getColumnIndexOrThrow(_cursor, "governorate");
      final int _cursorIndexOfManualAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "manualAddress");
      final int _cursorIndexOfLat = CursorUtil.getColumnIndexOrThrow(_cursor, "lat");
      final int _cursorIndexOfLng = CursorUtil.getColumnIndexOrThrow(_cursor, "lng");
      final int _cursorIndexOfImageUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "imageUrl");
      final int _cursorIndexOfReportType = CursorUtil.getColumnIndexOrThrow(_cursor, "reportType");
      final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
      final int _cursorIndexOfApproved = CursorUtil.getColumnIndexOrThrow(_cursor, "approved");
      final int _cursorIndexOfReporterId = CursorUtil.getColumnIndexOrThrow(_cursor, "reporterId");
      final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
      final int _cursorIndexOfFaceEmbedding = CursorUtil.getColumnIndexOrThrow(_cursor, "faceEmbedding");
      final int _cursorIndexOfSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "synced");
      final int _cursorIndexOfLastUpdated = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUpdated");
      final List<ReportEntity> _result = new ArrayList<ReportEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final ReportEntity _item;
        _item = new ReportEntity();
        if (_cursor.isNull(_cursorIndexOfReportId)) {
          _item.reportId = null;
        } else {
          _item.reportId = _cursor.getString(_cursorIndexOfReportId);
        }
        if (_cursor.isNull(_cursorIndexOfPersonName)) {
          _item.personName = null;
        } else {
          _item.personName = _cursor.getString(_cursorIndexOfPersonName);
        }
        if (_cursor.isNull(_cursorIndexOfDescription)) {
          _item.description = null;
        } else {
          _item.description = _cursor.getString(_cursorIndexOfDescription);
        }
        if (_cursor.isNull(_cursorIndexOfPersonAge)) {
          _item.personAge = null;
        } else {
          _item.personAge = _cursor.getString(_cursorIndexOfPersonAge);
        }
        if (_cursor.isNull(_cursorIndexOfPersonGender)) {
          _item.personGender = null;
        } else {
          _item.personGender = _cursor.getString(_cursorIndexOfPersonGender);
        }
        if (_cursor.isNull(_cursorIndexOfGovernorate)) {
          _item.governorate = null;
        } else {
          _item.governorate = _cursor.getString(_cursorIndexOfGovernorate);
        }
        if (_cursor.isNull(_cursorIndexOfManualAddress)) {
          _item.manualAddress = null;
        } else {
          _item.manualAddress = _cursor.getString(_cursorIndexOfManualAddress);
        }
        _item.lat = _cursor.getDouble(_cursorIndexOfLat);
        _item.lng = _cursor.getDouble(_cursorIndexOfLng);
        if (_cursor.isNull(_cursorIndexOfImageUrl)) {
          _item.imageUrl = null;
        } else {
          _item.imageUrl = _cursor.getString(_cursorIndexOfImageUrl);
        }
        if (_cursor.isNull(_cursorIndexOfReportType)) {
          _item.reportType = null;
        } else {
          _item.reportType = _cursor.getString(_cursorIndexOfReportType);
        }
        if (_cursor.isNull(_cursorIndexOfStatus)) {
          _item.status = null;
        } else {
          _item.status = _cursor.getString(_cursorIndexOfStatus);
        }
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfApproved);
        _item.approved = _tmp != 0;
        if (_cursor.isNull(_cursorIndexOfReporterId)) {
          _item.reporterId = null;
        } else {
          _item.reporterId = _cursor.getString(_cursorIndexOfReporterId);
        }
        _item.timestamp = _cursor.getLong(_cursorIndexOfTimestamp);
        if (_cursor.isNull(_cursorIndexOfFaceEmbedding)) {
          _item.faceEmbedding = null;
        } else {
          _item.faceEmbedding = _cursor.getString(_cursorIndexOfFaceEmbedding);
        }
        final int _tmp_1;
        _tmp_1 = _cursor.getInt(_cursorIndexOfSynced);
        _item.synced = _tmp_1 != 0;
        _item.lastUpdated = _cursor.getLong(_cursorIndexOfLastUpdated);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public LiveData<List<ReportEntity>> getFilteredReports(final String type, final String gov,
      final String q, final String status) {
    final String _sql = "SELECT * FROM reports WHERE approved = 1 AND status != 'resolved' AND (? = 'all' OR reportType = ?) AND (? = 'all' OR governorate = ?) AND (? = 'all' OR status = ?) AND (? = '' OR personName LIKE '%' || ? || '%'      OR reportId LIKE '%' || ? || '%'      OR manualAddress LIKE '%' || ? || '%') ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 10);
    int _argIndex = 1;
    if (type == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, type);
    }
    _argIndex = 2;
    if (type == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, type);
    }
    _argIndex = 3;
    if (gov == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, gov);
    }
    _argIndex = 4;
    if (gov == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, gov);
    }
    _argIndex = 5;
    if (status == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, status);
    }
    _argIndex = 6;
    if (status == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, status);
    }
    _argIndex = 7;
    if (q == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, q);
    }
    _argIndex = 8;
    if (q == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, q);
    }
    _argIndex = 9;
    if (q == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, q);
    }
    _argIndex = 10;
    if (q == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, q);
    }
    return __db.getInvalidationTracker().createLiveData(new String[] {"reports"}, false, new Callable<List<ReportEntity>>() {
      @Override
      @Nullable
      public List<ReportEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfReportId = CursorUtil.getColumnIndexOrThrow(_cursor, "reportId");
          final int _cursorIndexOfPersonName = CursorUtil.getColumnIndexOrThrow(_cursor, "personName");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfPersonAge = CursorUtil.getColumnIndexOrThrow(_cursor, "personAge");
          final int _cursorIndexOfPersonGender = CursorUtil.getColumnIndexOrThrow(_cursor, "personGender");
          final int _cursorIndexOfGovernorate = CursorUtil.getColumnIndexOrThrow(_cursor, "governorate");
          final int _cursorIndexOfManualAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "manualAddress");
          final int _cursorIndexOfLat = CursorUtil.getColumnIndexOrThrow(_cursor, "lat");
          final int _cursorIndexOfLng = CursorUtil.getColumnIndexOrThrow(_cursor, "lng");
          final int _cursorIndexOfImageUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "imageUrl");
          final int _cursorIndexOfReportType = CursorUtil.getColumnIndexOrThrow(_cursor, "reportType");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfApproved = CursorUtil.getColumnIndexOrThrow(_cursor, "approved");
          final int _cursorIndexOfReporterId = CursorUtil.getColumnIndexOrThrow(_cursor, "reporterId");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfFaceEmbedding = CursorUtil.getColumnIndexOrThrow(_cursor, "faceEmbedding");
          final int _cursorIndexOfSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "synced");
          final int _cursorIndexOfLastUpdated = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUpdated");
          final List<ReportEntity> _result = new ArrayList<ReportEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ReportEntity _item;
            _item = new ReportEntity();
            if (_cursor.isNull(_cursorIndexOfReportId)) {
              _item.reportId = null;
            } else {
              _item.reportId = _cursor.getString(_cursorIndexOfReportId);
            }
            if (_cursor.isNull(_cursorIndexOfPersonName)) {
              _item.personName = null;
            } else {
              _item.personName = _cursor.getString(_cursorIndexOfPersonName);
            }
            if (_cursor.isNull(_cursorIndexOfDescription)) {
              _item.description = null;
            } else {
              _item.description = _cursor.getString(_cursorIndexOfDescription);
            }
            if (_cursor.isNull(_cursorIndexOfPersonAge)) {
              _item.personAge = null;
            } else {
              _item.personAge = _cursor.getString(_cursorIndexOfPersonAge);
            }
            if (_cursor.isNull(_cursorIndexOfPersonGender)) {
              _item.personGender = null;
            } else {
              _item.personGender = _cursor.getString(_cursorIndexOfPersonGender);
            }
            if (_cursor.isNull(_cursorIndexOfGovernorate)) {
              _item.governorate = null;
            } else {
              _item.governorate = _cursor.getString(_cursorIndexOfGovernorate);
            }
            if (_cursor.isNull(_cursorIndexOfManualAddress)) {
              _item.manualAddress = null;
            } else {
              _item.manualAddress = _cursor.getString(_cursorIndexOfManualAddress);
            }
            _item.lat = _cursor.getDouble(_cursorIndexOfLat);
            _item.lng = _cursor.getDouble(_cursorIndexOfLng);
            if (_cursor.isNull(_cursorIndexOfImageUrl)) {
              _item.imageUrl = null;
            } else {
              _item.imageUrl = _cursor.getString(_cursorIndexOfImageUrl);
            }
            if (_cursor.isNull(_cursorIndexOfReportType)) {
              _item.reportType = null;
            } else {
              _item.reportType = _cursor.getString(_cursorIndexOfReportType);
            }
            if (_cursor.isNull(_cursorIndexOfStatus)) {
              _item.status = null;
            } else {
              _item.status = _cursor.getString(_cursorIndexOfStatus);
            }
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfApproved);
            _item.approved = _tmp != 0;
            if (_cursor.isNull(_cursorIndexOfReporterId)) {
              _item.reporterId = null;
            } else {
              _item.reporterId = _cursor.getString(_cursorIndexOfReporterId);
            }
            _item.timestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            if (_cursor.isNull(_cursorIndexOfFaceEmbedding)) {
              _item.faceEmbedding = null;
            } else {
              _item.faceEmbedding = _cursor.getString(_cursorIndexOfFaceEmbedding);
            }
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfSynced);
            _item.synced = _tmp_1 != 0;
            _item.lastUpdated = _cursor.getLong(_cursorIndexOfLastUpdated);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public List<ReportEntity> getFilteredReportsPaged(final String type, final String gov,
      final String q, final String status, final long cursor, final int pageSize) {
    final String _sql = "SELECT * FROM reports WHERE approved = 1 AND status != 'resolved' AND (? = 'all' OR reportType = ?) AND (? = 'all' OR governorate = ?) AND (? = 'all' OR status = ?) AND (? = '' OR personName LIKE '%' || ? || '%'      OR reportId LIKE '%' || ? || '%'      OR manualAddress LIKE '%' || ? || '%') AND (? = 0 OR timestamp < ?) ORDER BY timestamp DESC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 13);
    int _argIndex = 1;
    if (type == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, type);
    }
    _argIndex = 2;
    if (type == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, type);
    }
    _argIndex = 3;
    if (gov == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, gov);
    }
    _argIndex = 4;
    if (gov == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, gov);
    }
    _argIndex = 5;
    if (status == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, status);
    }
    _argIndex = 6;
    if (status == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, status);
    }
    _argIndex = 7;
    if (q == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, q);
    }
    _argIndex = 8;
    if (q == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, q);
    }
    _argIndex = 9;
    if (q == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, q);
    }
    _argIndex = 10;
    if (q == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, q);
    }
    _argIndex = 11;
    _statement.bindLong(_argIndex, cursor);
    _argIndex = 12;
    _statement.bindLong(_argIndex, cursor);
    _argIndex = 13;
    _statement.bindLong(_argIndex, pageSize);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfReportId = CursorUtil.getColumnIndexOrThrow(_cursor, "reportId");
      final int _cursorIndexOfPersonName = CursorUtil.getColumnIndexOrThrow(_cursor, "personName");
      final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
      final int _cursorIndexOfPersonAge = CursorUtil.getColumnIndexOrThrow(_cursor, "personAge");
      final int _cursorIndexOfPersonGender = CursorUtil.getColumnIndexOrThrow(_cursor, "personGender");
      final int _cursorIndexOfGovernorate = CursorUtil.getColumnIndexOrThrow(_cursor, "governorate");
      final int _cursorIndexOfManualAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "manualAddress");
      final int _cursorIndexOfLat = CursorUtil.getColumnIndexOrThrow(_cursor, "lat");
      final int _cursorIndexOfLng = CursorUtil.getColumnIndexOrThrow(_cursor, "lng");
      final int _cursorIndexOfImageUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "imageUrl");
      final int _cursorIndexOfReportType = CursorUtil.getColumnIndexOrThrow(_cursor, "reportType");
      final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
      final int _cursorIndexOfApproved = CursorUtil.getColumnIndexOrThrow(_cursor, "approved");
      final int _cursorIndexOfReporterId = CursorUtil.getColumnIndexOrThrow(_cursor, "reporterId");
      final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
      final int _cursorIndexOfFaceEmbedding = CursorUtil.getColumnIndexOrThrow(_cursor, "faceEmbedding");
      final int _cursorIndexOfSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "synced");
      final int _cursorIndexOfLastUpdated = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUpdated");
      final List<ReportEntity> _result = new ArrayList<ReportEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final ReportEntity _item;
        _item = new ReportEntity();
        if (_cursor.isNull(_cursorIndexOfReportId)) {
          _item.reportId = null;
        } else {
          _item.reportId = _cursor.getString(_cursorIndexOfReportId);
        }
        if (_cursor.isNull(_cursorIndexOfPersonName)) {
          _item.personName = null;
        } else {
          _item.personName = _cursor.getString(_cursorIndexOfPersonName);
        }
        if (_cursor.isNull(_cursorIndexOfDescription)) {
          _item.description = null;
        } else {
          _item.description = _cursor.getString(_cursorIndexOfDescription);
        }
        if (_cursor.isNull(_cursorIndexOfPersonAge)) {
          _item.personAge = null;
        } else {
          _item.personAge = _cursor.getString(_cursorIndexOfPersonAge);
        }
        if (_cursor.isNull(_cursorIndexOfPersonGender)) {
          _item.personGender = null;
        } else {
          _item.personGender = _cursor.getString(_cursorIndexOfPersonGender);
        }
        if (_cursor.isNull(_cursorIndexOfGovernorate)) {
          _item.governorate = null;
        } else {
          _item.governorate = _cursor.getString(_cursorIndexOfGovernorate);
        }
        if (_cursor.isNull(_cursorIndexOfManualAddress)) {
          _item.manualAddress = null;
        } else {
          _item.manualAddress = _cursor.getString(_cursorIndexOfManualAddress);
        }
        _item.lat = _cursor.getDouble(_cursorIndexOfLat);
        _item.lng = _cursor.getDouble(_cursorIndexOfLng);
        if (_cursor.isNull(_cursorIndexOfImageUrl)) {
          _item.imageUrl = null;
        } else {
          _item.imageUrl = _cursor.getString(_cursorIndexOfImageUrl);
        }
        if (_cursor.isNull(_cursorIndexOfReportType)) {
          _item.reportType = null;
        } else {
          _item.reportType = _cursor.getString(_cursorIndexOfReportType);
        }
        if (_cursor.isNull(_cursorIndexOfStatus)) {
          _item.status = null;
        } else {
          _item.status = _cursor.getString(_cursorIndexOfStatus);
        }
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfApproved);
        _item.approved = _tmp != 0;
        if (_cursor.isNull(_cursorIndexOfReporterId)) {
          _item.reporterId = null;
        } else {
          _item.reporterId = _cursor.getString(_cursorIndexOfReporterId);
        }
        _item.timestamp = _cursor.getLong(_cursorIndexOfTimestamp);
        if (_cursor.isNull(_cursorIndexOfFaceEmbedding)) {
          _item.faceEmbedding = null;
        } else {
          _item.faceEmbedding = _cursor.getString(_cursorIndexOfFaceEmbedding);
        }
        final int _tmp_1;
        _tmp_1 = _cursor.getInt(_cursorIndexOfSynced);
        _item.synced = _tmp_1 != 0;
        _item.lastUpdated = _cursor.getLong(_cursorIndexOfLastUpdated);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public long getOldestTimestamp(final String type, final String gov, final String status) {
    final String _sql = "SELECT MIN(timestamp) FROM reports WHERE approved = 1 AND (? = 'all' OR reportType = ?) AND (? = 'all' OR governorate = ?) AND (? = 'all' OR status = ?)";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 6);
    int _argIndex = 1;
    if (type == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, type);
    }
    _argIndex = 2;
    if (type == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, type);
    }
    _argIndex = 3;
    if (gov == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, gov);
    }
    _argIndex = 4;
    if (gov == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, gov);
    }
    _argIndex = 5;
    if (status == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, status);
    }
    _argIndex = 6;
    if (status == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, status);
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final long _result;
      if (_cursor.moveToFirst()) {
        _result = _cursor.getLong(0);
      } else {
        _result = 0L;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public LiveData<List<ReportEntity>> getReportsByReporter(final String uid) {
    final String _sql = "SELECT * FROM reports WHERE reporterId = ? ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (uid == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, uid);
    }
    return __db.getInvalidationTracker().createLiveData(new String[] {"reports"}, false, new Callable<List<ReportEntity>>() {
      @Override
      @Nullable
      public List<ReportEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfReportId = CursorUtil.getColumnIndexOrThrow(_cursor, "reportId");
          final int _cursorIndexOfPersonName = CursorUtil.getColumnIndexOrThrow(_cursor, "personName");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfPersonAge = CursorUtil.getColumnIndexOrThrow(_cursor, "personAge");
          final int _cursorIndexOfPersonGender = CursorUtil.getColumnIndexOrThrow(_cursor, "personGender");
          final int _cursorIndexOfGovernorate = CursorUtil.getColumnIndexOrThrow(_cursor, "governorate");
          final int _cursorIndexOfManualAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "manualAddress");
          final int _cursorIndexOfLat = CursorUtil.getColumnIndexOrThrow(_cursor, "lat");
          final int _cursorIndexOfLng = CursorUtil.getColumnIndexOrThrow(_cursor, "lng");
          final int _cursorIndexOfImageUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "imageUrl");
          final int _cursorIndexOfReportType = CursorUtil.getColumnIndexOrThrow(_cursor, "reportType");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfApproved = CursorUtil.getColumnIndexOrThrow(_cursor, "approved");
          final int _cursorIndexOfReporterId = CursorUtil.getColumnIndexOrThrow(_cursor, "reporterId");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfFaceEmbedding = CursorUtil.getColumnIndexOrThrow(_cursor, "faceEmbedding");
          final int _cursorIndexOfSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "synced");
          final int _cursorIndexOfLastUpdated = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUpdated");
          final List<ReportEntity> _result = new ArrayList<ReportEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ReportEntity _item;
            _item = new ReportEntity();
            if (_cursor.isNull(_cursorIndexOfReportId)) {
              _item.reportId = null;
            } else {
              _item.reportId = _cursor.getString(_cursorIndexOfReportId);
            }
            if (_cursor.isNull(_cursorIndexOfPersonName)) {
              _item.personName = null;
            } else {
              _item.personName = _cursor.getString(_cursorIndexOfPersonName);
            }
            if (_cursor.isNull(_cursorIndexOfDescription)) {
              _item.description = null;
            } else {
              _item.description = _cursor.getString(_cursorIndexOfDescription);
            }
            if (_cursor.isNull(_cursorIndexOfPersonAge)) {
              _item.personAge = null;
            } else {
              _item.personAge = _cursor.getString(_cursorIndexOfPersonAge);
            }
            if (_cursor.isNull(_cursorIndexOfPersonGender)) {
              _item.personGender = null;
            } else {
              _item.personGender = _cursor.getString(_cursorIndexOfPersonGender);
            }
            if (_cursor.isNull(_cursorIndexOfGovernorate)) {
              _item.governorate = null;
            } else {
              _item.governorate = _cursor.getString(_cursorIndexOfGovernorate);
            }
            if (_cursor.isNull(_cursorIndexOfManualAddress)) {
              _item.manualAddress = null;
            } else {
              _item.manualAddress = _cursor.getString(_cursorIndexOfManualAddress);
            }
            _item.lat = _cursor.getDouble(_cursorIndexOfLat);
            _item.lng = _cursor.getDouble(_cursorIndexOfLng);
            if (_cursor.isNull(_cursorIndexOfImageUrl)) {
              _item.imageUrl = null;
            } else {
              _item.imageUrl = _cursor.getString(_cursorIndexOfImageUrl);
            }
            if (_cursor.isNull(_cursorIndexOfReportType)) {
              _item.reportType = null;
            } else {
              _item.reportType = _cursor.getString(_cursorIndexOfReportType);
            }
            if (_cursor.isNull(_cursorIndexOfStatus)) {
              _item.status = null;
            } else {
              _item.status = _cursor.getString(_cursorIndexOfStatus);
            }
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfApproved);
            _item.approved = _tmp != 0;
            if (_cursor.isNull(_cursorIndexOfReporterId)) {
              _item.reporterId = null;
            } else {
              _item.reporterId = _cursor.getString(_cursorIndexOfReporterId);
            }
            _item.timestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            if (_cursor.isNull(_cursorIndexOfFaceEmbedding)) {
              _item.faceEmbedding = null;
            } else {
              _item.faceEmbedding = _cursor.getString(_cursorIndexOfFaceEmbedding);
            }
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfSynced);
            _item.synced = _tmp_1 != 0;
            _item.lastUpdated = _cursor.getLong(_cursorIndexOfLastUpdated);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public List<String> getAllIds() {
    final String _sql = "SELECT reportId FROM reports";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final List<String> _result = new ArrayList<String>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final String _item;
        if (_cursor.isNull(0)) {
          _item = null;
        } else {
          _item = _cursor.getString(0);
        }
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<ReportEntity> getPendingSync() {
    final String _sql = "SELECT * FROM reports WHERE synced = 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfReportId = CursorUtil.getColumnIndexOrThrow(_cursor, "reportId");
      final int _cursorIndexOfPersonName = CursorUtil.getColumnIndexOrThrow(_cursor, "personName");
      final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
      final int _cursorIndexOfPersonAge = CursorUtil.getColumnIndexOrThrow(_cursor, "personAge");
      final int _cursorIndexOfPersonGender = CursorUtil.getColumnIndexOrThrow(_cursor, "personGender");
      final int _cursorIndexOfGovernorate = CursorUtil.getColumnIndexOrThrow(_cursor, "governorate");
      final int _cursorIndexOfManualAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "manualAddress");
      final int _cursorIndexOfLat = CursorUtil.getColumnIndexOrThrow(_cursor, "lat");
      final int _cursorIndexOfLng = CursorUtil.getColumnIndexOrThrow(_cursor, "lng");
      final int _cursorIndexOfImageUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "imageUrl");
      final int _cursorIndexOfReportType = CursorUtil.getColumnIndexOrThrow(_cursor, "reportType");
      final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
      final int _cursorIndexOfApproved = CursorUtil.getColumnIndexOrThrow(_cursor, "approved");
      final int _cursorIndexOfReporterId = CursorUtil.getColumnIndexOrThrow(_cursor, "reporterId");
      final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
      final int _cursorIndexOfFaceEmbedding = CursorUtil.getColumnIndexOrThrow(_cursor, "faceEmbedding");
      final int _cursorIndexOfSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "synced");
      final int _cursorIndexOfLastUpdated = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUpdated");
      final List<ReportEntity> _result = new ArrayList<ReportEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final ReportEntity _item;
        _item = new ReportEntity();
        if (_cursor.isNull(_cursorIndexOfReportId)) {
          _item.reportId = null;
        } else {
          _item.reportId = _cursor.getString(_cursorIndexOfReportId);
        }
        if (_cursor.isNull(_cursorIndexOfPersonName)) {
          _item.personName = null;
        } else {
          _item.personName = _cursor.getString(_cursorIndexOfPersonName);
        }
        if (_cursor.isNull(_cursorIndexOfDescription)) {
          _item.description = null;
        } else {
          _item.description = _cursor.getString(_cursorIndexOfDescription);
        }
        if (_cursor.isNull(_cursorIndexOfPersonAge)) {
          _item.personAge = null;
        } else {
          _item.personAge = _cursor.getString(_cursorIndexOfPersonAge);
        }
        if (_cursor.isNull(_cursorIndexOfPersonGender)) {
          _item.personGender = null;
        } else {
          _item.personGender = _cursor.getString(_cursorIndexOfPersonGender);
        }
        if (_cursor.isNull(_cursorIndexOfGovernorate)) {
          _item.governorate = null;
        } else {
          _item.governorate = _cursor.getString(_cursorIndexOfGovernorate);
        }
        if (_cursor.isNull(_cursorIndexOfManualAddress)) {
          _item.manualAddress = null;
        } else {
          _item.manualAddress = _cursor.getString(_cursorIndexOfManualAddress);
        }
        _item.lat = _cursor.getDouble(_cursorIndexOfLat);
        _item.lng = _cursor.getDouble(_cursorIndexOfLng);
        if (_cursor.isNull(_cursorIndexOfImageUrl)) {
          _item.imageUrl = null;
        } else {
          _item.imageUrl = _cursor.getString(_cursorIndexOfImageUrl);
        }
        if (_cursor.isNull(_cursorIndexOfReportType)) {
          _item.reportType = null;
        } else {
          _item.reportType = _cursor.getString(_cursorIndexOfReportType);
        }
        if (_cursor.isNull(_cursorIndexOfStatus)) {
          _item.status = null;
        } else {
          _item.status = _cursor.getString(_cursorIndexOfStatus);
        }
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfApproved);
        _item.approved = _tmp != 0;
        if (_cursor.isNull(_cursorIndexOfReporterId)) {
          _item.reporterId = null;
        } else {
          _item.reporterId = _cursor.getString(_cursorIndexOfReporterId);
        }
        _item.timestamp = _cursor.getLong(_cursorIndexOfTimestamp);
        if (_cursor.isNull(_cursorIndexOfFaceEmbedding)) {
          _item.faceEmbedding = null;
        } else {
          _item.faceEmbedding = _cursor.getString(_cursorIndexOfFaceEmbedding);
        }
        final int _tmp_1;
        _tmp_1 = _cursor.getInt(_cursorIndexOfSynced);
        _item.synced = _tmp_1 != 0;
        _item.lastUpdated = _cursor.getLong(_cursorIndexOfLastUpdated);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public ReportEntity getById(final String id) {
    final String _sql = "SELECT * FROM reports WHERE reportId = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (id == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, id);
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfReportId = CursorUtil.getColumnIndexOrThrow(_cursor, "reportId");
      final int _cursorIndexOfPersonName = CursorUtil.getColumnIndexOrThrow(_cursor, "personName");
      final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
      final int _cursorIndexOfPersonAge = CursorUtil.getColumnIndexOrThrow(_cursor, "personAge");
      final int _cursorIndexOfPersonGender = CursorUtil.getColumnIndexOrThrow(_cursor, "personGender");
      final int _cursorIndexOfGovernorate = CursorUtil.getColumnIndexOrThrow(_cursor, "governorate");
      final int _cursorIndexOfManualAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "manualAddress");
      final int _cursorIndexOfLat = CursorUtil.getColumnIndexOrThrow(_cursor, "lat");
      final int _cursorIndexOfLng = CursorUtil.getColumnIndexOrThrow(_cursor, "lng");
      final int _cursorIndexOfImageUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "imageUrl");
      final int _cursorIndexOfReportType = CursorUtil.getColumnIndexOrThrow(_cursor, "reportType");
      final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
      final int _cursorIndexOfApproved = CursorUtil.getColumnIndexOrThrow(_cursor, "approved");
      final int _cursorIndexOfReporterId = CursorUtil.getColumnIndexOrThrow(_cursor, "reporterId");
      final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
      final int _cursorIndexOfFaceEmbedding = CursorUtil.getColumnIndexOrThrow(_cursor, "faceEmbedding");
      final int _cursorIndexOfSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "synced");
      final int _cursorIndexOfLastUpdated = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUpdated");
      final ReportEntity _result;
      if (_cursor.moveToFirst()) {
        _result = new ReportEntity();
        if (_cursor.isNull(_cursorIndexOfReportId)) {
          _result.reportId = null;
        } else {
          _result.reportId = _cursor.getString(_cursorIndexOfReportId);
        }
        if (_cursor.isNull(_cursorIndexOfPersonName)) {
          _result.personName = null;
        } else {
          _result.personName = _cursor.getString(_cursorIndexOfPersonName);
        }
        if (_cursor.isNull(_cursorIndexOfDescription)) {
          _result.description = null;
        } else {
          _result.description = _cursor.getString(_cursorIndexOfDescription);
        }
        if (_cursor.isNull(_cursorIndexOfPersonAge)) {
          _result.personAge = null;
        } else {
          _result.personAge = _cursor.getString(_cursorIndexOfPersonAge);
        }
        if (_cursor.isNull(_cursorIndexOfPersonGender)) {
          _result.personGender = null;
        } else {
          _result.personGender = _cursor.getString(_cursorIndexOfPersonGender);
        }
        if (_cursor.isNull(_cursorIndexOfGovernorate)) {
          _result.governorate = null;
        } else {
          _result.governorate = _cursor.getString(_cursorIndexOfGovernorate);
        }
        if (_cursor.isNull(_cursorIndexOfManualAddress)) {
          _result.manualAddress = null;
        } else {
          _result.manualAddress = _cursor.getString(_cursorIndexOfManualAddress);
        }
        _result.lat = _cursor.getDouble(_cursorIndexOfLat);
        _result.lng = _cursor.getDouble(_cursorIndexOfLng);
        if (_cursor.isNull(_cursorIndexOfImageUrl)) {
          _result.imageUrl = null;
        } else {
          _result.imageUrl = _cursor.getString(_cursorIndexOfImageUrl);
        }
        if (_cursor.isNull(_cursorIndexOfReportType)) {
          _result.reportType = null;
        } else {
          _result.reportType = _cursor.getString(_cursorIndexOfReportType);
        }
        if (_cursor.isNull(_cursorIndexOfStatus)) {
          _result.status = null;
        } else {
          _result.status = _cursor.getString(_cursorIndexOfStatus);
        }
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfApproved);
        _result.approved = _tmp != 0;
        if (_cursor.isNull(_cursorIndexOfReporterId)) {
          _result.reporterId = null;
        } else {
          _result.reporterId = _cursor.getString(_cursorIndexOfReporterId);
        }
        _result.timestamp = _cursor.getLong(_cursorIndexOfTimestamp);
        if (_cursor.isNull(_cursorIndexOfFaceEmbedding)) {
          _result.faceEmbedding = null;
        } else {
          _result.faceEmbedding = _cursor.getString(_cursorIndexOfFaceEmbedding);
        }
        final int _tmp_1;
        _tmp_1 = _cursor.getInt(_cursorIndexOfSynced);
        _result.synced = _tmp_1 != 0;
        _result.lastUpdated = _cursor.getLong(_cursorIndexOfLastUpdated);
      } else {
        _result = null;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public LiveData<List<ReportEntity>> search(final String query) {
    final String _sql = "SELECT * FROM reports WHERE approved = 1 AND (personName LIKE '%' || ? || '%' OR governorate LIKE '%' || ? || '%') ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 2;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    return __db.getInvalidationTracker().createLiveData(new String[] {"reports"}, false, new Callable<List<ReportEntity>>() {
      @Override
      @Nullable
      public List<ReportEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfReportId = CursorUtil.getColumnIndexOrThrow(_cursor, "reportId");
          final int _cursorIndexOfPersonName = CursorUtil.getColumnIndexOrThrow(_cursor, "personName");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfPersonAge = CursorUtil.getColumnIndexOrThrow(_cursor, "personAge");
          final int _cursorIndexOfPersonGender = CursorUtil.getColumnIndexOrThrow(_cursor, "personGender");
          final int _cursorIndexOfGovernorate = CursorUtil.getColumnIndexOrThrow(_cursor, "governorate");
          final int _cursorIndexOfManualAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "manualAddress");
          final int _cursorIndexOfLat = CursorUtil.getColumnIndexOrThrow(_cursor, "lat");
          final int _cursorIndexOfLng = CursorUtil.getColumnIndexOrThrow(_cursor, "lng");
          final int _cursorIndexOfImageUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "imageUrl");
          final int _cursorIndexOfReportType = CursorUtil.getColumnIndexOrThrow(_cursor, "reportType");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfApproved = CursorUtil.getColumnIndexOrThrow(_cursor, "approved");
          final int _cursorIndexOfReporterId = CursorUtil.getColumnIndexOrThrow(_cursor, "reporterId");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfFaceEmbedding = CursorUtil.getColumnIndexOrThrow(_cursor, "faceEmbedding");
          final int _cursorIndexOfSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "synced");
          final int _cursorIndexOfLastUpdated = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUpdated");
          final List<ReportEntity> _result = new ArrayList<ReportEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ReportEntity _item;
            _item = new ReportEntity();
            if (_cursor.isNull(_cursorIndexOfReportId)) {
              _item.reportId = null;
            } else {
              _item.reportId = _cursor.getString(_cursorIndexOfReportId);
            }
            if (_cursor.isNull(_cursorIndexOfPersonName)) {
              _item.personName = null;
            } else {
              _item.personName = _cursor.getString(_cursorIndexOfPersonName);
            }
            if (_cursor.isNull(_cursorIndexOfDescription)) {
              _item.description = null;
            } else {
              _item.description = _cursor.getString(_cursorIndexOfDescription);
            }
            if (_cursor.isNull(_cursorIndexOfPersonAge)) {
              _item.personAge = null;
            } else {
              _item.personAge = _cursor.getString(_cursorIndexOfPersonAge);
            }
            if (_cursor.isNull(_cursorIndexOfPersonGender)) {
              _item.personGender = null;
            } else {
              _item.personGender = _cursor.getString(_cursorIndexOfPersonGender);
            }
            if (_cursor.isNull(_cursorIndexOfGovernorate)) {
              _item.governorate = null;
            } else {
              _item.governorate = _cursor.getString(_cursorIndexOfGovernorate);
            }
            if (_cursor.isNull(_cursorIndexOfManualAddress)) {
              _item.manualAddress = null;
            } else {
              _item.manualAddress = _cursor.getString(_cursorIndexOfManualAddress);
            }
            _item.lat = _cursor.getDouble(_cursorIndexOfLat);
            _item.lng = _cursor.getDouble(_cursorIndexOfLng);
            if (_cursor.isNull(_cursorIndexOfImageUrl)) {
              _item.imageUrl = null;
            } else {
              _item.imageUrl = _cursor.getString(_cursorIndexOfImageUrl);
            }
            if (_cursor.isNull(_cursorIndexOfReportType)) {
              _item.reportType = null;
            } else {
              _item.reportType = _cursor.getString(_cursorIndexOfReportType);
            }
            if (_cursor.isNull(_cursorIndexOfStatus)) {
              _item.status = null;
            } else {
              _item.status = _cursor.getString(_cursorIndexOfStatus);
            }
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfApproved);
            _item.approved = _tmp != 0;
            if (_cursor.isNull(_cursorIndexOfReporterId)) {
              _item.reporterId = null;
            } else {
              _item.reporterId = _cursor.getString(_cursorIndexOfReporterId);
            }
            _item.timestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            if (_cursor.isNull(_cursorIndexOfFaceEmbedding)) {
              _item.faceEmbedding = null;
            } else {
              _item.faceEmbedding = _cursor.getString(_cursorIndexOfFaceEmbedding);
            }
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfSynced);
            _item.synced = _tmp_1 != 0;
            _item.lastUpdated = _cursor.getLong(_cursorIndexOfLastUpdated);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public List<ReportEntity> getLatestN(final int n) {
    final String _sql = "SELECT * FROM reports WHERE approved = 1 ORDER BY timestamp DESC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, n);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfReportId = CursorUtil.getColumnIndexOrThrow(_cursor, "reportId");
      final int _cursorIndexOfPersonName = CursorUtil.getColumnIndexOrThrow(_cursor, "personName");
      final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
      final int _cursorIndexOfPersonAge = CursorUtil.getColumnIndexOrThrow(_cursor, "personAge");
      final int _cursorIndexOfPersonGender = CursorUtil.getColumnIndexOrThrow(_cursor, "personGender");
      final int _cursorIndexOfGovernorate = CursorUtil.getColumnIndexOrThrow(_cursor, "governorate");
      final int _cursorIndexOfManualAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "manualAddress");
      final int _cursorIndexOfLat = CursorUtil.getColumnIndexOrThrow(_cursor, "lat");
      final int _cursorIndexOfLng = CursorUtil.getColumnIndexOrThrow(_cursor, "lng");
      final int _cursorIndexOfImageUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "imageUrl");
      final int _cursorIndexOfReportType = CursorUtil.getColumnIndexOrThrow(_cursor, "reportType");
      final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
      final int _cursorIndexOfApproved = CursorUtil.getColumnIndexOrThrow(_cursor, "approved");
      final int _cursorIndexOfReporterId = CursorUtil.getColumnIndexOrThrow(_cursor, "reporterId");
      final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
      final int _cursorIndexOfFaceEmbedding = CursorUtil.getColumnIndexOrThrow(_cursor, "faceEmbedding");
      final int _cursorIndexOfSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "synced");
      final int _cursorIndexOfLastUpdated = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUpdated");
      final List<ReportEntity> _result = new ArrayList<ReportEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final ReportEntity _item;
        _item = new ReportEntity();
        if (_cursor.isNull(_cursorIndexOfReportId)) {
          _item.reportId = null;
        } else {
          _item.reportId = _cursor.getString(_cursorIndexOfReportId);
        }
        if (_cursor.isNull(_cursorIndexOfPersonName)) {
          _item.personName = null;
        } else {
          _item.personName = _cursor.getString(_cursorIndexOfPersonName);
        }
        if (_cursor.isNull(_cursorIndexOfDescription)) {
          _item.description = null;
        } else {
          _item.description = _cursor.getString(_cursorIndexOfDescription);
        }
        if (_cursor.isNull(_cursorIndexOfPersonAge)) {
          _item.personAge = null;
        } else {
          _item.personAge = _cursor.getString(_cursorIndexOfPersonAge);
        }
        if (_cursor.isNull(_cursorIndexOfPersonGender)) {
          _item.personGender = null;
        } else {
          _item.personGender = _cursor.getString(_cursorIndexOfPersonGender);
        }
        if (_cursor.isNull(_cursorIndexOfGovernorate)) {
          _item.governorate = null;
        } else {
          _item.governorate = _cursor.getString(_cursorIndexOfGovernorate);
        }
        if (_cursor.isNull(_cursorIndexOfManualAddress)) {
          _item.manualAddress = null;
        } else {
          _item.manualAddress = _cursor.getString(_cursorIndexOfManualAddress);
        }
        _item.lat = _cursor.getDouble(_cursorIndexOfLat);
        _item.lng = _cursor.getDouble(_cursorIndexOfLng);
        if (_cursor.isNull(_cursorIndexOfImageUrl)) {
          _item.imageUrl = null;
        } else {
          _item.imageUrl = _cursor.getString(_cursorIndexOfImageUrl);
        }
        if (_cursor.isNull(_cursorIndexOfReportType)) {
          _item.reportType = null;
        } else {
          _item.reportType = _cursor.getString(_cursorIndexOfReportType);
        }
        if (_cursor.isNull(_cursorIndexOfStatus)) {
          _item.status = null;
        } else {
          _item.status = _cursor.getString(_cursorIndexOfStatus);
        }
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfApproved);
        _item.approved = _tmp != 0;
        if (_cursor.isNull(_cursorIndexOfReporterId)) {
          _item.reporterId = null;
        } else {
          _item.reporterId = _cursor.getString(_cursorIndexOfReporterId);
        }
        _item.timestamp = _cursor.getLong(_cursorIndexOfTimestamp);
        if (_cursor.isNull(_cursorIndexOfFaceEmbedding)) {
          _item.faceEmbedding = null;
        } else {
          _item.faceEmbedding = _cursor.getString(_cursorIndexOfFaceEmbedding);
        }
        final int _tmp_1;
        _tmp_1 = _cursor.getInt(_cursorIndexOfSynced);
        _item.synced = _tmp_1 != 0;
        _item.lastUpdated = _cursor.getLong(_cursorIndexOfLastUpdated);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public int getApprovedCount() {
    final String _sql = "SELECT COUNT(*) FROM reports WHERE approved = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _result;
      if (_cursor.moveToFirst()) {
        _result = _cursor.getInt(0);
      } else {
        _result = 0;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public int countApproved() {
    final String _sql = "SELECT COUNT(*) FROM reports WHERE approved = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _result;
      if (_cursor.moveToFirst()) {
        _result = _cursor.getInt(0);
      } else {
        _result = 0;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public int countByType(final String type) {
    final String _sql = "SELECT COUNT(*) FROM reports WHERE approved = 1 AND reportType = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (type == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, type);
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _result;
      if (_cursor.moveToFirst()) {
        _result = _cursor.getInt(0);
      } else {
        _result = 0;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public int countByReporter(final String uid) {
    final String _sql = "SELECT COUNT(*) FROM reports WHERE reporterId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (uid == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, uid);
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _result;
      if (_cursor.moveToFirst()) {
        _result = _cursor.getInt(0);
      } else {
        _result = 0;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public int getResolvedCount() {
    final String _sql = "SELECT COUNT(*) FROM reports WHERE status = 'resolved'";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _result;
      if (_cursor.moveToFirst()) {
        _result = _cursor.getInt(0);
      } else {
        _result = 0;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public void deleteNotInList(final List<String> activeIds) {
    __db.assertNotSuspendingTransaction();
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("DELETE FROM reports WHERE reportId NOT IN (");
    final int _inputSize = activeIds == null ? 1 : activeIds.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(")");
    final String _sql = _stringBuilder.toString();
    final SupportSQLiteStatement _stmt = __db.compileStatement(_sql);
    int _argIndex = 1;
    if (activeIds == null) {
      _stmt.bindNull(_argIndex);
    } else {
      for (String _item : activeIds) {
        if (_item == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, _item);
        }
        _argIndex++;
      }
    }
    __db.beginTransaction();
    try {
      _stmt.executeUpdateDelete();
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
