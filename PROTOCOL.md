# Protocolo de Comunicación

## Comandos

### 1. Crear Paciente
**Cliente → Servidor:**
CREATE_PATIENT
full_name:<nombre_completo>
document_id:<identificación>
age:<edad>
sex:<M/F>
contact_email:<correo>
registration_date:<YYYY-MM-DD>
clinical_notes:<notas_clínicas>
checksum_fasta:<hash>
file_size_bytes:<tamaño>
FASTA_FILE

patient_id
<secuencia_genómica>
END_FASTA

 Copiar**Servidor → Cliente (Éxito):**
SUCCESS
patient_id:<id_asignado>
 Copiar**Servidor → Cliente (Error):**
ERROR
message:<descripción_del_error>
 Copiar---

### 2. Obtener Paciente
**Cliente → Servidor:**
GET_PATIENT
patient_id:<id>
 Copiar**Servidor → Cliente:**
PATIENT_DATA
patient_id:<id>
full_name:<nombre>
document_id:<identificación>
age:<edad>
sex:<M/F>
contact_email:<correo>
registration_date:<YYYY-MM-DD>
clinical_notes:<notas_clínicas>
FASTA_FILE

patient_id
<secuencia_genómica>
END_FASTA

 Copiar---

### 3. Actualizar Paciente
**Cliente → Servidor:**
UPDATE_PATIENT
patient_id:<id>
<campos_a_actualizar>
FASTA_FILE

patient_id
<secuencia_genómica>
END_FASTA

 Copiar---

### 4. Eliminar Paciente
**Cliente → Servidor:**
DELETE_PATIENT
patient_id:<id>
 Copiar**Servidor → Cliente (Éxito):**
SUCCESS
 Copiar**Servidor → Cliente (Error):**
ERROR
message:<descripción_del_error>
 Copiar---

## Códigos de Error
- `ERROR: Invalid FASTA format`
- `ERROR: Missing metadata`
- `ERROR: Duplicate document ID`