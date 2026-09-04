package com.brutiful.netprobe.network

import android.content.Context
import androidx.room.*
import com.brutiful.netprobe.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Dao
interface LiveConnectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(connection: LiveConnection)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(connections: List<LiveConnection>)

    @Query("SELECT * FROM live_connections")
    fun getAllFlow(): Flow<List<LiveConnection>>

    @Query("SELECT * FROM live_connections")
    suspend fun getAll(): List<LiveConnection>

    @Query("DELETE FROM live_connections")
    suspend fun deleteAllConnections()

    @Update
    suspend fun update(connection: LiveConnection)
}

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: ConnectionHistory)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(history: List<ConnectionHistory>)

    @Query("SELECT * FROM connection_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<ConnectionHistory>>

    @Query("DELETE FROM connection_history")
    suspend fun deleteAllHistory()
}

@Dao
interface DeviceIdentityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(identity: DeviceIdentity)

    @Query("SELECT * FROM device_identities")
    suspend fun getAll(): List<DeviceIdentity>

    @Query("SELECT * FROM device_identities WHERE macAddress = :mac")
    suspend fun getByMac(mac: String): DeviceIdentity?

    @Query("SELECT * FROM device_identities WHERE lastIpAddress = :ip")
    suspend fun getByIp(ip: String): DeviceIdentity?
}

@Dao
interface PortInfoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(ports: List<PortInfo>)

    @Query("SELECT * FROM port_info")
    fun getAllFlow(): Flow<List<PortInfo>>

    @Query("SELECT * FROM port_info")
    suspend fun getAll(): List<PortInfo>

    @Query("SELECT * FROM port_info WHERE port = :port")
    suspend fun getByPort(port: Int): PortInfo?
}

@Database(entities = [ConnectionHistory::class, DeviceIdentity::class, LiveConnection::class, PortInfo::class], version = 4, exportSchema = false)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun deviceIdentityDao(): DeviceIdentityDao
    abstract fun liveConnectionDao(): LiveConnectionDao
    abstract fun portInfoDao(): PortInfoDao

    companion object {
        @Volatile
        private var INSTANCE: HistoryDatabase? = null

        fun getDatabase(context: Context): HistoryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.inMemoryDatabaseBuilder(
                    context.applicationContext,
                    HistoryDatabase::class.java
                )
                .fallbackToDestructiveMigration()
                .build()
                
                INSTANCE = instance
                
                // Populate initial port data
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        if (instance.portInfoDao().getAll().isEmpty()) {
                            instance.portInfoDao().insertAll(PortData.initialPorts)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                instance
            }
        }
    }
}
