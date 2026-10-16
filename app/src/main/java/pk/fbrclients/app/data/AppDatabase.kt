package pk.fbrclients.app.data

import android.content.Context
import android.util.Base64
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import pk.fbrclients.app.security.KeystoreCipher
import java.security.SecureRandom

/**
 * Schema version 1 is the first one shipped with a stable signing key.
 * Any later change to the tables needs a version bump AND a Migration,
 * so that clients' data is never lost.
 */
@Database(
    entities = [Client::class, Category::class, PropertySubType::class, WealthStatement::class, WealthItem::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clients(): ClientDao
    abstract fun wealth(): WealthDao
    abstract fun backup(): BackupDao

    companion object {
        private const val FILE = "fbr_clients.db"

        /** Opens the SQLCipher-encrypted database. */
        fun open(context: Context): AppDatabase {
            System.loadLibrary("sqlcipher")
            val key = DatabaseKey(context).get()
            return Room.databaseBuilder(context, AppDatabase::class.java, FILE)
                .openHelperFactory(SupportOpenHelperFactory(key))
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) = Seed.insert(db)
                })
                .build()
        }
    }
}

/**
 * A random 256-bit database key, created once. It is stored only in
 * encrypted form; the Android Keystore key that unwraps it never leaves the phone.
 */
private class DatabaseKey(context: Context) {
    private val prefs = context.getSharedPreferences("db", Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher("fbr_db_key")

    fun get(): ByteArray {
        prefs.getString(PREF, null)?.let {
            return cipher.decrypt(Base64.decode(it, Base64.NO_WRAP))
        }
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        // commit() (not apply()): the key must be on disk before the database is created.
        prefs.edit().putString(PREF, Base64.encodeToString(cipher.encrypt(key), Base64.NO_WRAP)).commit()
        return key
    }

    private companion object {
        const val PREF = "wrapped_key"
    }
}
