package com.example.tableau.repository;

import com.example.tableau.entity.TableauServer;
import com.example.tableau.enums.CollibraSyncStatus;
import com.example.tableau.enums.StatusFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TableauServerRepository extends JpaRepository<TableauServer, Long> {

    Optional<TableauServer> findByAssetId(String assetId);

    List<TableauServer> findByStatusFlag(StatusFlag statusFlag);

    List<TableauServer> findByStatusFlagNot(StatusFlag statusFlag);

    List<TableauServer> findByCollibraSyncStatus(CollibraSyncStatus collibraSyncStatus);

    List<TableauServer> findByCollibraSyncStatusIn(List<CollibraSyncStatus> collibraSyncStatuses);

    @Modifying
    @Query("UPDATE TableauServer s SET s.statusFlag = :statusFlag WHERE s.assetId = :assetId")
    int updateStatusFlagByAssetId(@Param("assetId") String assetId, @Param("statusFlag") StatusFlag statusFlag);

    @Modifying
    @Query("UPDATE TableauServer s SET s.collibraSyncStatus = :collibraSyncStatus WHERE s.assetId = :assetId")
    int updateCollibraSyncStatusByAssetId(@Param("assetId") String assetId, @Param("collibraSyncStatus") CollibraSyncStatus collibraSyncStatus);

    @Modifying
    @Query("UPDATE TableauServer s SET s.collibraSyncStatus = :collibraSyncStatus WHERE s.id = :id")
    int updateCollibraSyncStatusById(@Param("id") Long id, @Param("collibraSyncStatus") CollibraSyncStatus collibraSyncStatus);

    @Query("SELECT s FROM TableauServer s WHERE s.statusFlag != 'DELETED'")
    List<TableauServer> findAllActive();

    /**
     * Find all servers that need to be synced to Collibra.
     * Returns servers with collibraSyncStatus of NOT_SYNCED, PENDING_SYNC, PENDING_UPDATE, or PENDING_DELETE.
     */
    @Query("SELECT s FROM TableauServer s WHERE s.collibraSyncStatus IN ('NOT_SYNCED', 'PENDING_SYNC', 'PENDING_UPDATE', 'PENDING_DELETE') AND s.statusFlag != 'DELETED'")
    List<TableauServer> findAllPendingCollibraSync();

    boolean existsByAssetId(String assetId);
}
