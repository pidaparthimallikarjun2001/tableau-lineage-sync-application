package com.example.tableau.repository;

import com.example.tableau.entity.TableauWorksheet;
import com.example.tableau.enums.StatusFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TableauWorksheetRepository extends JpaRepository<TableauWorksheet, Long> {

    Optional<TableauWorksheet> findByAssetIdAndSiteId(String assetId, String siteId);

    Optional<TableauWorksheet> findByAssetId(String assetId);

    List<TableauWorksheet> findBySiteId(String siteId);

    @Query("SELECT w FROM TableauWorksheet w WHERE w.workbook.id = :workbookDbId")
    List<TableauWorksheet> findByWorkbookDbId(@Param("workbookDbId") Long workbookDbId);

    List<TableauWorksheet> findByStatusFlag(StatusFlag statusFlag);

    List<TableauWorksheet> findByStatusFlagNot(StatusFlag statusFlag);

    @Modifying
    @Query("UPDATE TableauWorksheet w SET w.statusFlag = :statusFlag WHERE w.assetId = :assetId AND w.siteId = :siteId")
    int updateStatusFlagByAssetIdAndSiteId(@Param("assetId") String assetId, @Param("siteId") String siteId, @Param("statusFlag") StatusFlag statusFlag);

    @Modifying
    @Query("UPDATE TableauWorksheet w SET w.statusFlag = :statusFlag WHERE w.workbook.id = :workbookDbId")
    int updateStatusFlagByWorkbookDbId(@Param("workbookDbId") Long workbookDbId, @Param("statusFlag") StatusFlag statusFlag);

    @Query("SELECT w FROM TableauWorksheet w WHERE w.statusFlag != 'DELETED'")
    List<TableauWorksheet> findAllActive();

    @Query("SELECT w FROM TableauWorksheet w WHERE w.siteId = :siteId AND w.statusFlag != 'DELETED'")
    List<TableauWorksheet> findAllActiveBySiteId(@Param("siteId") String siteId);

    /**
     * Find all worksheets with their workbook relationship eagerly loaded.
     * This method avoids N+1 query problems and lazy loading exceptions during Collibra ingestion
     * by fetching all related entities in a single query.
     * 
     * @return List of all worksheets with workbook relationships loaded
     */
    @Query("SELECT DISTINCT w FROM TableauWorksheet w LEFT JOIN FETCH w.workbook")
    List<TableauWorksheet> findAllWithWorkbook();

    boolean existsByAssetIdAndSiteId(String assetId, String siteId);

    /**
     * Find all worksheets for a specific site with their workbook relationship eagerly loaded.
     * This method avoids N+1 query problems and lazy loading exceptions during Collibra ingestion
     * by fetching all related entities in a single query.
     * 
     * @param siteId the Tableau site ID (assetId of the site)
     * @return List of worksheets for the site with workbook relationships loaded
     */
    @Query("SELECT DISTINCT w FROM TableauWorksheet w LEFT JOIN FETCH w.workbook WHERE w.siteId = :siteId")
    List<TableauWorksheet> findAllBySiteIdWithWorkbook(@Param("siteId") String siteId);
}
