package pk.fbrclients.app.data

/** Everything in the database, for the encrypted backup file. */
data class BackupData(
    val clients: List<Client>,
    val categories: List<Category>,
    val subTypes: List<PropertySubType>,
    val statements: List<WealthStatement>,
    val items: List<WealthItem>,
    val bills: List<ElectricityBill> = emptyList(),
    /** Empty in backups made before electricity bills existed: then the phone keeps its own. */
    val discos: List<Disco> = emptyList(),
)
