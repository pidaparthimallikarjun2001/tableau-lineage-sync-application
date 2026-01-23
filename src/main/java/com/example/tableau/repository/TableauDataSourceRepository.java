package com.example.tableau.repository;

import com.example.tableau.entity.TableauDataSource;
import com.example.tableau.enums.SourceType;
import com.example.tableau.enums.StatusFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TableauDataSourceRepository extends JpaRepository<TableauDataSource, Long> {

    Optional<TableauDataSource> findByAssetIdAndSiteId(String assetId, String siteId);

    Optional<TableauDataSource> findByAssetId(String assetId);

    List<TableauDataSource> findBySiteId(String siteId);

    @Query("SELECT d FROM TableauDataSource d WHERE d.workbook.id = :workbookDbId")
    List<TableauDataSource> findByWorkbookDbId(@Param("workbookDbId") Long workbookDbId);

    List<TableauDataSource> findBySourceType(SourceType sourceType);

    List<TableauDataSource> findByStatusFlag(StatusFlag statusFlag);

    List<TableauDataSource> findByStatusFlagNot(StatusFlag statusFlag);

    List<TableauDataSource> findByIsPublishedTrue();

    List<TableauDataSource> findByIsCertifiedTrue();

    @Modifying
    @Query("UPDATE TableauDataSource d SET d.statusFlag = :statusFlag WHERE d.assetId = :assetId AND d.siteId = :siteId")
    int updateStatusFlagByAssetIdAndSiteId(@Param("assetId") String assetId, @Param("siteId") String siteId, @Param("statusFlag") StatusFlag statusFlag);

    @Modifying
    @Query("UPDATE TableauDataSource d SET d.statusFlag = :statusFlag WHERE d.workbook.id = :workbookDbId")
    int updateStatusFlagByWorkbookDbId(@Param("workbookDbId") Long workbookDbId, @Param("statusFlag") StatusFlag statusFlag);

    @Query("SELECT d FROM TableauDataSource d WHERE d.statusFlag != 'DELETED'")
    List<TableauDataSource> findAllActive();

    @Query("SELECT d FROM TableauDataSource d WHERE d.siteId = :siteId AND d.statusFlag != 'DELETED'")
    List<TableauDataSource> findAllActiveBySiteId(@Param("siteId") String siteId);

    boolean existsByAssetIdAndSiteId(String assetId, String siteId);
}
