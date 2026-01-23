package com.example.tableau.repository;

import com.example.tableau.entity.TableauWorkbook;
import com.example.tableau.enums.StatusFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TableauWorkbookRepository extends JpaRepository<TableauWorkbook, Long> {

    Optional<TableauWorkbook> findByAssetIdAndSiteId(String assetId, String siteId);

    Optional<TableauWorkbook> findByAssetId(String assetId);

    List<TableauWorkbook> findBySiteId(String siteId);

    @Query("SELECT w FROM TableauWorkbook w WHERE w.project.id = :projectDbId")
    List<TableauWorkbook> findByProjectDbId(@Param("projectDbId") Long projectDbId);

    List<TableauWorkbook> findByStatusFlag(StatusFlag statusFlag);

    List<TableauWorkbook> findByStatusFlagNot(StatusFlag statusFlag);

    @Modifying
    @Query("UPDATE TableauWorkbook w SET w.statusFlag = :statusFlag WHERE w.assetId = :assetId AND w.siteId = :siteId")
    int updateStatusFlagByAssetIdAndSiteId(@Param("assetId") String assetId, @Param("siteId") String siteId, @Param("statusFlag") StatusFlag statusFlag);

    @Modifying
    @Query("UPDATE TableauWorkbook w SET w.statusFlag = :statusFlag WHERE w.project.id = :projectDbId")
    int updateStatusFlagByProjectDbId(@Param("projectDbId") Long projectDbId, @Param("statusFlag") StatusFlag statusFlag);

    @Query("SELECT w FROM TableauWorkbook w WHERE w.statusFlag != 'DELETED'")
    List<TableauWorkbook> findAllActive();

    @Query("SELECT w FROM TableauWorkbook w WHERE w.siteId = :siteId AND w.statusFlag != 'DELETED'")
    List<TableauWorkbook> findAllActiveBySiteId(@Param("siteId") String siteId);

    boolean existsByAssetIdAndSiteId(String assetId, String siteId);
}
