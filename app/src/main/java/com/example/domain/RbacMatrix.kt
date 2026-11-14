package com.example.domain

import com.example.model.AppRole
import com.example.model.Permission

object RbacMatrix {
    private val matrix: Map<AppRole, Set<Permission>> = mapOf(
        AppRole.SUPER_ADMIN to setOf(
            Permission.ORGS_VIEW_ALL,
            Permission.ORGS_MANAGE,
            Permission.USERS_VIEW_ALL,
            Permission.USERS_MANAGE_ALL,
            Permission.CATALOGUE_VIEW,
            Permission.SCANS_VIEW_ALL,
            Permission.SCANS_RUN_ANY_ORG,
            Permission.FINDINGS_VIEW_ALL,
            Permission.FINDINGS_MANAGE_ALL,
            Permission.AUDIT_VIEW_ALL,
            Permission.EVIDENCE_VIEW_ALL,
            Permission.EVIDENCE_UPLOAD,
            Permission.EVIDENCE_MANAGE_ALL,
            Permission.SOC_VIEW_ALL,
            Permission.SOC_MANAGE_ALL,
            Permission.THREATS_VIEW,
            Permission.THREATS_CONTRIBUTE,
            Permission.THREATS_SYNC,
            Permission.AWARENESS_MANAGE,
            Permission.GOVERNANCE_VIEW,
            Permission.GOVERNANCE_MANAGE,
            Permission.ACFS_MANAGE,
            Permission.CII_MANAGE,
        ),
        AppRole.ORG_ADMIN to setOf(
            Permission.ORG_VIEW_OWN,
            Permission.ORG_EDIT_OWN,
            Permission.USERS_VIEW_ORG,
            Permission.USERS_MANAGE_ORG,
            Permission.CATALOGUE_VIEW,
            Permission.SCANS_VIEW_ORG,
            Permission.SCANS_RUN_ORG,
            Permission.FINDINGS_VIEW_ORG,
            Permission.FINDINGS_MANAGE_ORG,
            Permission.AUDIT_VIEW_ORG,
            Permission.EVIDENCE_VIEW_ORG,
            Permission.EVIDENCE_UPLOAD,
            Permission.EVIDENCE_MANAGE_ORG,
            Permission.SOC_VIEW_ORG,
            Permission.SOC_MANAGE_ORG,
            Permission.THREATS_VIEW,
            Permission.THREATS_CONTRIBUTE,
            Permission.THREATS_SYNC,
            Permission.CII_VIEW_OWN,
        ),
        AppRole.COMPLIANCE_OFFICER to setOf(
            Permission.ORG_VIEW_OWN,
            Permission.CATALOGUE_VIEW,
            Permission.SCANS_VIEW_ORG,
            Permission.SCANS_RUN_ORG,
            Permission.FINDINGS_VIEW_ORG,
            Permission.FINDINGS_MANAGE_ORG,
            Permission.EVIDENCE_VIEW_ORG,
            Permission.EVIDENCE_UPLOAD,
            Permission.SOC_VIEW_ORG,
            Permission.SOC_MANAGE_ORG,
            Permission.THREATS_VIEW,
            Permission.THREATS_CONTRIBUTE,
            Permission.THREATS_SYNC,
            Permission.CII_VIEW_OWN,
        ),
        AppRole.AUDITOR to setOf(
            Permission.ORG_VIEW_OWN,
            Permission.CATALOGUE_VIEW,
            Permission.SCANS_VIEW_ORG,
            Permission.FINDINGS_VIEW_ORG,
            Permission.AUDIT_VIEW_ORG,
            Permission.EVIDENCE_VIEW_ORG,
            Permission.SOC_VIEW_ORG,
            Permission.THREATS_VIEW,
        ),
        AppRole.VIEWER to setOf(
            Permission.ORG_VIEW_OWN,
            Permission.CATALOGUE_VIEW,
            Permission.SCANS_VIEW_ORG,
            Permission.FINDINGS_VIEW_ORG,
            Permission.EVIDENCE_VIEW_ORG,
            Permission.SOC_VIEW_ORG,
            Permission.THREATS_VIEW,
        ),
    )

    fun permissionsOf(role: AppRole?): Set<Permission> = role?.let { matrix[it] }.orEmpty()

    fun can(role: AppRole?, permission: Permission): Boolean = permissionsOf(role).contains(permission)

    val assignableOrgRoles: List<AppRole> = listOf(
        AppRole.ORG_ADMIN,
        AppRole.COMPLIANCE_OFFICER,
        AppRole.AUDITOR,
        AppRole.VIEWER,
    )
}
