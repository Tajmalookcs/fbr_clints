package pk.fbrclients.app.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

const val HELD_BY_OTHERS_CODE = "7014"

/** Groups in IRIS order. Stored by name. */
enum class AssetGroup(val title: String, val isLiability: Boolean = false) {
    IMMOVABLE("Immovable Properties (Non-Business)"),
    FINANCIAL("Financial Assets & Investments (Non-Business)"),
    MOVEABLE("Moveable Assets (Non-Business)"),
    OTHER("Other Assets"),
    LIABILITY("Liabilities", isLiability = true),
}

/**
 * A category under a group, e.g. 7008 Motor Vehicle(s).
 * [code] is null when the IRIS code is not known yet (the owner adds it in Settings).
 * Built-in categories keep their name; only [editable] ones let the code be changed.
 */
@Entity(tableName = "category")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String?,
    val name: String,
    @ColumnInfo(name = "grp") val group: AssetGroup,
    val isProperty: Boolean = false,
    val sortOrder: Int = 0,
    val builtIn: Boolean = false,
    val editable: Boolean = true,
) {
    val isLiability get() = group.isLiability

    /** 7014: asset held in someone else's name; the item can record the holder's CNIC. */
    val isHeldByOthers get() = code == HELD_BY_OTHERS_CODE

    /** "Residential Property" -> "Residential", used in the property description. */
    val propertyType get() = name.removeSuffix(" Property")
}

@Entity(
    tableName = "property_sub_type",
    foreignKeys = [ForeignKey(Category::class, ["id"], ["categoryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("categoryId")],
)
data class PropertySubType(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val name: String,
)

@Entity(
    tableName = "wealth_statement",
    foreignKeys = [ForeignKey(Client::class, ["id"], ["clientId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["clientId", "taxYear"], unique = true)],
)
data class WealthStatement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    val taxYear: Int,
    /** 703002. Null = take it from the previous year's statement automatically. */
    val prevYearNetAssets: Long? = null,
    /** 7031 */
    val incomeNormal: Long = 0,
    /** 7032 */
    val incomeExempt: Long = 0,
    /** 7033 */
    val incomeFinal: Long = 0,
    /** 7087 (personal / household expenses) */
    val personalExpenses: Long = 0,
)

@Entity(
    tableName = "wealth_item",
    foreignKeys = [
        ForeignKey(WealthStatement::class, ["id"], ["statementId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Category::class, ["id"], ["categoryId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("statementId"), Index("categoryId")],
)
data class WealthItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val statementId: Long,
    val categoryId: Long,
    val description: String,
    /** Whole rupees; may be 0 (e.g. inherited or dowry items). */
    val amount: Long,
    // Property-only fields (null for other items)
    val subType: String? = null,
    /** yyyy-MM-dd */
    val acquisitionDate: String? = null,
    val areaUnit: String? = null,
    /** Kept as text so "1.38" stays exactly 1.38. */
    val area: String? = null,
    val propertyAddress: String? = null,
    /** 7014 only: CNIC of the person the asset is held by (12345-1234567-1). */
    val holderCnic: String? = null,
)

/** A statement with its net assets, for the list on the client page. */
data class StatementSummary(
    @Embedded val statement: WealthStatement,
    val netAssets: Long,
)

object AreaUnits {
    val ALL = listOf("Acre", "Kanal", "Marla", "Square Foot", "Square Meter")
}

/** "[Type] – [Sub-type], [Area] [Unit], [Property Address]" */
fun propertyDescription(type: String, subType: String, area: String, unit: String, address: String): String =
    buildString {
        append(type.trim())
        if (subType.isNotBlank()) append(" – ").append(subType.trim())
        val areaPart = listOf(area.trim(), unit.trim()).filter { it.isNotEmpty() }.joinToString(" ")
        if (areaPart.isNotEmpty()) append(", ").append(areaPart)
        if (address.isNotBlank()) append(", ").append(address.trim().replace(Regex("\\s*,?\\s*\\n\\s*"), ", "))
    }
